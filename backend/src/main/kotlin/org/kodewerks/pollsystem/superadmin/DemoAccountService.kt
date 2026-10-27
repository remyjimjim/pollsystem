package org.kodewerks.pollsystem.superadmin

import org.kodewerks.pollsystem.authz.RoleAuthCache
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.PollEventType
import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.RoleAssignment
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.poll.PollHistoryService
import org.kodewerks.pollsystem.repository.CountyZipsRepository
import org.kodewerks.pollsystem.repository.RoleAssignmentRepository
import org.kodewerks.pollsystem.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant

/** The demo account's address on this site (DEMO_EMAIL in fly.toml / fly.staging.toml). */
@ConfigurationProperties(prefix = "app.demo")
data class DemoProperties(
    val email: String = "demo@test.local",
    val hours: Long = 12
)

/** The demo as the Super dashboard shows it. */
data class DemoStatus(
    val email: String,
    val on: Boolean,
    val until: Instant? = null,
    val zipcode: String? = null,
    val startedBy: String? = null
)

/**
 * The demo account (decided 2026-10-28): one shared address per site that a
 * super switches on for 12 hours at a time, so a prospective customer can try
 * BallotBox without an email, code or payment. While on, typing the address
 * on Sign in signs straight in as a paid member and creator (a Creator grant
 * for the demo zipcode's state). When it ends (its time runs out, or a super
 * presses End now), its polls are archived and handed to the super who started
 * it, and the account is deleted with its votes and comments. Starting it again
 * makes a fresh, empty account.
 */
@Service
class DemoAccountService(
    private val props: DemoProperties,
    private val users: UserRepository,
    private val grants: RoleAssignmentRepository,
    private val countyZips: CountyZipsRepository,
    private val roleAuthCache: RoleAuthCache,
    private val deletion: AccountDeletion,
    private val history: PollHistoryService,
    private val jdbc: JdbcTemplate,
    private val tx: TransactionTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    private fun demoUser(): User? = users.findByEmail(props.email)?.takeIf { it.demoUntil != null }

    /** The demo account while it's on (null when off, or once its time has run out). */
    fun active(): User? = demoUser()?.takeIf { it.demoUntil!!.isAfter(Instant.now()) && it.isEnabled }

    fun isDemoAddress(email: String) = users.findByEmail(email)?.let { it.id == active()?.id } == true

    fun status(): DemoStatus {
        val demo = active() ?: return DemoStatus(props.email, on = false)
        return DemoStatus(props.email, true, demo.demoUntil, demo.zipcode,
            demo.demoStartedBy?.let { users.findById(it).orElse(null)?.email })
    }

    fun start(by: User, zipcode: String): DemoStatus {
        demoUser()?.let { if (active() != null) throw conflict("The demo is already on; use +12 hours or End now") else end() }
        if (users.findByEmail(props.email) != null) throw conflict("${props.email} belongs to a real account")
        val place = countyZips.findByZipcode(zipcode).firstOrNull()
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown zipcode: $zipcode")
        val until = Instant.now().plus(Duration.ofHours(props.hours))
        tx.executeWithoutResult {
            val demo = users.save(User(
                email = props.email, phone = placeholderPhone(), zipcode = zipcode,
                access = AccessLevel.CREATOR, isEnabled = true,
                paidUntil = until, demoUntil = until, demoStartedBy = by.id
            ))
            grants.save(RoleAssignment(user = demo, role = AccessLevel.CREATOR, scopeLevel = ScopeLevel.STATE,
                state = place.county.state, enabled = true))
        }
        roleAuthCache.invalidateAuthorizations()
        log.info("Demo account {} started by super {} at {} until {}", props.email, by.id, zipcode, until)
        return status()
    }

    fun extend(): DemoStatus {
        val demo = active() ?: throw conflict("The demo is off; press Start")
        val until = demo.demoUntil!!.plus(Duration.ofHours(props.hours))
        users.save(demo.copy(demoUntil = until, paidUntil = until))
        return status()
    }

    /** Ends the demo now: hand its polls to the super who started it, archived, then delete it. */
    fun end(): DemoStatus {
        val demo = demoUser() ?: return status()
        val heir = demo.demoStartedBy?.let { users.findById(it).orElse(null) }
            ?: users.findByAccess(AccessLevel.SUPER).minByOrNull { it.id }
            ?: throw conflict("No super to hand the demo's polls to")
        tx.executeWithoutResult {
            for ((table, kind) in listOf("questionnaires" to PollKind.QUESTIONNAIRE, "elections" to PollKind.ELECTION,
                                         "ballot_measures" to PollKind.BALLOT_MEASURE)) {
                val ids = jdbc.queryForList("select id from $table where creator_id = ?", Long::class.java, demo.id)
                ids.forEach { history.record(kind, it, PollEventType.ARCHIVED, heir, "The demo account ended") }
                jdbc.update("update $table set status = 'ARCHIVED'::poll_status, creator_id = ? where creator_id = ?", heir.id, demo.id)
            }
        }
        deletion.deleteDemo(demo.id)
        log.info("Demo account {} ended; its polls went to {}", props.email, heir.id)
        return status()
    }

    /** Ends the demo once its time has run out (checked every 5 minutes). */
    @Scheduled(fixedDelayString = "\${app.demo.sweep-interval-ms:300000}", initialDelay = 60_000)
    fun sweep() {
        val demo = demoUser() ?: return
        if (!demo.demoUntil!!.isAfter(Instant.now())) end()
    }

    // A unique, obviously fake number: the phone column is unique by its digits (V39).
    private fun placeholderPhone(): String {
        repeat(20) {
            val p = "+1555" + "%07d".format(random.nextInt(10_000_000))
            if (!users.existsByPhone(p)) return p
        }
        throw conflict("Couldn't pick a placeholder phone; try again")
    }

    private fun conflict(msg: String) = ResponseStatusException(HttpStatus.CONFLICT, msg)
}
