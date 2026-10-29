package org.kodewerks.pollsystem.substack

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.kodewerks.pollsystem.auth.MagicLinkEmailer
import org.kodewerks.pollsystem.auth.MagicLinkService
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64

/*
 * Paid Substack subscribers become BallotBox members (decided 2026-10-28).
 * Substack bills through its own, separate Stripe account (norfstar.substack;
 * never the BallotBox one). That account's webhook tells us when someone pays,
 * renews or cancels; a re-sync (Super button, and every 5 minutes) compares
 * against its active subscriptions to catch anything missed.
 */

/** The Substack Stripe account: its webhook signing secret, and a read-only key for re-sync. */
@ConfigurationProperties(prefix = "app.stripe-substack")
data class StripeSubstackProperties(
    val webhookSecret: String = "",
    val apiKey: String = ""
)

@Entity
@Table(name = "substack_sync_log")
data class SubstackSyncEntry(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(nullable = false) val at: Instant = Instant.now(),
    @Column(nullable = false, length = 30) val source: String,
    val email: String? = null,
    @Column(nullable = false, length = 40) val action: String,
    @Column(nullable = false) val ok: Boolean,
    val message: String? = null
)

@Repository
interface SubstackSyncLogRepository : JpaRepository<SubstackSyncEntry, Long> {
    fun findAllByOrderByIdDesc(page: PageRequest): List<SubstackSyncEntry>
}

/**
 * Records what each event or re-sync did. Errors also go to the backend log,
 * which scripts/pollsystem.watchall.bash follows, as
 * "source : stripe-substack : 10/29/2026:4:05:00 : ERROR: message" (Pacific time).
 */
@Service
class SubstackSyncLog(private val entries: SubstackSyncLogRepository) {
    private val log = LoggerFactory.getLogger("stripe-substack")
    private val stamp = DateTimeFormatter.ofPattern("MM/dd/yyyy:H:mm:ss").withZone(ZoneId.of("America/Los_Angeles"))

    fun ok(source: String, action: String, email: String? = null, message: String? = null) {
        entries.save(SubstackSyncEntry(source = source, email = email, action = action, ok = true, message = message))
        log.info("{} {} {} {}", source, action, email ?: "", message ?: "")
    }

    /** Returns the formatted line, for showing on screen too. */
    fun error(source: String, message: String, email: String? = null): String {
        val line = "source : $SERVICE : ${stamp.format(Instant.now())} : ERROR: $message"
        log.error(line)
        runCatching { entries.save(SubstackSyncEntry(source = source, email = email, action = "error", ok = false, message = line)) }
        return line
    }

    fun recent(n: Int = 50) = entries.findAllByOrderByIdDesc(PageRequest.of(0, n))

    companion object { const val SERVICE = "stripe-substack" }
}

/** Membership changes driven by the Substack Stripe account. */
@Service
class SubstackMembership(
    private val users: UserRepository,
    private val magicLinks: MagicLinkService,
    private val emailer: MagicLinkEmailer,
    private val syncLog: SubstackSyncLog
) {
    /**
     * Paid through [until] (the end of what they paid for). An unknown email is
     * provisioned (phone and zipcode come at first sign-in) and emailed a
     * sign-in link. Returns the action taken, or null if nothing changed.
     */
    @Transactional
    fun paidThrough(email: String, customerId: String, until: Instant, source: String): String? {
        val normalized = email.trim().lowercase()
        val existing = users.findByEmail(normalized) ?: users.findBySubstackCustomerId(customerId)
        if (existing == null) {
            val user = users.save(User(email = normalized, access = AccessLevel.USER, isEnabled = true,
                paidUntil = until, substackCustomerId = customerId))
            emailer.send(user, magicLinks.issueToken(user))
            syncLog.ok(source, "activated", normalized, "new member, paid until $until")
            return "activated"
        }
        // Never shorten: a member who also pays BallotBox directly may be paid further ahead.
        val newUntil = listOfNotNull(existing.paidUntil, until).max()
        if (newUntil == existing.paidUntil && existing.substackCustomerId == customerId && existing.access != AccessLevel.VIEWER) return null
        val access = if (existing.access == AccessLevel.VIEWER) AccessLevel.USER else existing.access
        users.save(existing.copy(paidUntil = newUntil, access = access, substackCustomerId = customerId))
        syncLog.ok(source, "renewed", existing.email, "paid until $newUntil")
        return "renewed"
    }

    /**
     * Their Substack subscription ended. Someone who also pays BallotBox directly
     * keeps that membership (only the Substack link is dropped); otherwise they
     * lapse to VIEWER, as when a BallotBox subscription ends.
     */
    @Transactional
    fun ended(customerId: String, source: String): Boolean {
        val user = users.findBySubstackCustomerId(customerId) ?: return false
        if (!user.stripeSubscriptionId.isNullOrBlank()) {
            users.save(user.copy(substackCustomerId = null))
            syncLog.ok(source, "ended", user.email, "Substack ended; still paying BallotBox directly, membership kept")
            return true
        }
        val access = if (user.access == AccessLevel.SUPER) user.access else AccessLevel.VIEWER
        users.save(user.copy(paidUntil = Instant.now(), access = access, substackCustomerId = null))
        syncLog.ok(source, "ended", user.email, "Substack subscription ended")
        return true
    }
}

/** Reads the Substack Stripe account's events and subscriptions (field names from old and new API versions). */
object StripeSubstackJson {
    /** An invoice's paid-through time: the latest period end on its lines. */
    fun invoicePaidThrough(invoice: JsonNode): Instant? =
        invoice.path("lines").path("data").mapNotNull { it.path("period").path("end").takeIf { e -> e.isNumber }?.asLong() }
            .maxOrNull()?.let(Instant::ofEpochSecond)

    /** A subscription's current period end: on the subscription (older API) or its items (newer). */
    fun subscriptionPeriodEnd(sub: JsonNode): Instant? {
        val top = sub.path("current_period_end").takeIf { it.isNumber }?.asLong()
        val items = sub.path("items").path("data").mapNotNull { it.path("current_period_end").takeIf { e -> e.isNumber }?.asLong() }.maxOrNull()
        return (top ?: items)?.let(Instant::ofEpochSecond)
    }

    fun customerId(obj: JsonNode): String? = obj.path("customer").let {
        when { it.isTextual -> it.asText(); it.isObject -> it.path("id").asText(null); else -> null }
    }

    /** Statuses that count as a paying member (past_due: Stripe is still retrying the card). */
    val MEMBER_STATUSES = setOf("active", "trialing", "past_due")
}

/**
 * Re-sync: every subscription in the Substack Stripe account. Members get
 * paid through their period end; linked members with no live subscription
 * end. Runs from the Super page and every 5 minutes (only once a key is set).
 */
@Service
class SubstackResync(
    private val props: StripeSubstackProperties,
    private val membership: SubstackMembership,
    private val users: UserRepository,
    private val syncLog: SubstackSyncLog,
    private val json: ObjectMapper
) {
    data class Result(val checked: Int, val activated: Int, val renewed: Int, val ended: Int, val errors: List<String>)

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    private val grace = Duration.ofDays(1)

    fun configured() = props.apiKey.isNotBlank()

    fun run(source: String): Result {
        if (!configured()) {
            val line = syncLog.error(source, "Re-sync isn't set up: STRIPE_SUBSTACK_API_KEY is not set")
            return Result(0, 0, 0, 0, listOf(line))
        }
        val errors = mutableListOf<String>()
        var activated = 0; var renewed = 0; var ended = 0
        val subs = try { listSubscriptions() } catch (e: Exception) {
            return Result(0, 0, 0, 0, listOf(syncLog.error(source, "Couldn't list subscriptions: ${e.message}")))
        }
        val live = mutableSetOf<String>()
        for (sub in subs) {
            val status = sub.path("status").asText()
            val customer = StripeSubstackJson.customerId(sub) ?: continue
            if (status !in StripeSubstackJson.MEMBER_STATUSES) continue
            live += customer
            val email = sub.path("customer").path("email").asText(null)
            val end = StripeSubstackJson.subscriptionPeriodEnd(sub)
            if (email.isNullOrBlank() || end == null) {
                errors += syncLog.error(source, "Subscription ${sub.path("id").asText()} has no customer email or period end")
                continue
            }
            try {
                when (membership.paidThrough(email, customer, end.plus(grace), source)) {
                    "activated" -> activated++
                    "renewed" -> renewed++
                }
            } catch (e: Exception) {
                errors += syncLog.error(source, "Couldn't apply $email: ${e.message}", email)
            }
        }
        for (u in users.findBySubstackCustomerIdIsNotNull()) {
            if (u.substackCustomerId !in live && runCatching { membership.ended(u.substackCustomerId!!, source) }.getOrElse {
                    errors += syncLog.error(source, "Couldn't end ${u.email}: ${it.message}", u.email); false }) ended++
        }
        val result = Result(subs.size, activated, renewed, ended, errors)
        if (source != "resync-scheduled" || activated + renewed + ended > 0 || errors.isNotEmpty()) {
            syncLog.ok(source, "summary", message = "checked ${subs.size}, activated $activated, renewed $renewed, ended $ended, errors ${errors.size}")
        }
        return result
    }

    @Scheduled(fixedDelayString = "\${app.stripe-substack.resync-interval-ms:300000}", initialDelay = 90_000)
    fun scheduled() {
        if (configured()) run("resync-scheduled")
    }

    private fun listSubscriptions(): List<JsonNode> {
        val out = mutableListOf<JsonNode>()
        var after: String? = null
        val auth = "Basic " + Base64.getEncoder().encodeToString("${props.apiKey}:".toByteArray())
        repeat(100) {
            val q = buildString {
                append("status=all&limit=100&expand%5B%5D=data.customer")
                if (after != null) append("&starting_after=").append(URLEncoder.encode(after, Charsets.UTF_8))
            }
            val res = http.send(HttpRequest.newBuilder(URI("https://api.stripe.com/v1/subscriptions?$q"))
                .header("Authorization", auth).timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString())
            val body = json.readTree(res.body())
            if (res.statusCode() != 200) error(body.path("error").path("message").asText("HTTP ${res.statusCode()}"))
            val page = body.path("data").toList()
            out += page
            if (!body.path("has_more").asBoolean(false) || page.isEmpty()) return out
            after = page.last().path("id").asText()
        }
        return out
    }
}
