package org.kodewerks.pollsystem.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Simple in-memory rate limiting (fixed windows), per machine. Guards the
 * public endpoints that cost money or invite abuse: sign-in links (email),
 * the account-status check, starting checkout, and search.
 *
 * Off under the `local` profile and in tests (app.rate-limit.enabled), so e2e
 * runs can sign in as often as they like. Per machine, so with N machines the
 * effective limit is up to N times higher, which is fine for abuse control.
 */
@Component
class RateLimiter(
    @Value("\${app.rate-limit.enabled:true}") val enabled: Boolean
) {
    /** Swappable in tests. */
    var clock: Clock = Clock.systemUTC()

    /** A limit: at most [max] in each [window]. */
    data class Rule(val name: String, val max: Int, val window: Duration)

    private class Window(val start: Instant, var count: Int)
    private val windows = ConcurrentHashMap<String, Window>()

    /**
     * Count one use of [rule] by [key]. Returns null if allowed, or the
     * seconds until the window resets if over the limit.
     */
    fun tryAcquire(rule: Rule, key: String): Long? {
        if (!enabled) return null
        val now = clock.instant()
        val w = windows.compute("${rule.name}|$key") { _, old ->
            if (old == null || !now.isBefore(old.start.plus(rule.window))) Window(now, 1)
            else old.also { it.count++ }
        }!!
        if (w.count <= rule.max) return null
        return Duration.between(now, w.start.plus(rule.window)).seconds.coerceAtLeast(1)
    }

    /** Like [tryAcquire], but throws a 429 (with Retry-After) when over the limit. */
    fun check(rule: Rule, key: String) {
        tryAcquire(rule, key)?.let { throw TooManyRequests(it) }
    }

    /** Forget finished windows so the map doesn't grow without bound. */
    @Scheduled(fixedDelay = 600_000)
    fun sweep() {
        val now = clock.instant()
        windows.entries.removeIf { now.isAfter(it.value.start.plus(Duration.ofHours(2))) }
    }

    companion object {
        // Per IP (RateLimitFilter).
        val MAGIC_LINK_IP = Rule("magic-link-ip", 10, Duration.ofMinutes(15))
        val STATUS_IP = Rule("status-ip", 30, Duration.ofMinutes(1))
        val CHECKOUT_IP = Rule("checkout-ip", 10, Duration.ofHours(1))
        val SEARCH_IP = Rule("search-ip", 120, Duration.ofMinutes(1))
        // Per email address (AuthController).
        val MAGIC_LINK_EMAIL = Rule("magic-link-email", 3, Duration.ofMinutes(15))
        val CHECKOUT_EMAIL = Rule("checkout-email", 5, Duration.ofHours(1))
    }
}

/** HTTP 429 with a Retry-After header and a message the pages can show. */
class TooManyRequests(private val retryAfterSeconds: Long) :
    ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please wait a few minutes and try again.") {
    override fun getHeaders(): HttpHeaders = HttpHeaders().apply { set(HttpHeaders.RETRY_AFTER, retryAfterSeconds.toString()) }
}
