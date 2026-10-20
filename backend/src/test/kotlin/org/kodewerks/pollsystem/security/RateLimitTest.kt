package org.kodewerks.pollsystem.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/** Rate limits, switched on with the proxy secret configured (as on staging/prod). */
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.rate-limit.enabled=true", "app.proxy.secret=test-proxy-secret"])
class RateLimitTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var fixtures: TestFixtures

    private fun status(email: String, viaProxyAs: String? = null, secret: String = "test-proxy-secret") =
        mockMvc.post("/api/auth/status") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to email))
            if (viaProxyAs != null) { header("X-Proxy-Secret", secret); header("X-Client-IP", viaProxyAs) }
        }

    @Test
    fun `per IP via the proxy, 30 status checks a minute, then 429 with Retry-After, and other visitors are unaffected`() {
        repeat(30) { status("a$it@x.test", viaProxyAs = "203.0.113.1").andExpect { status { isOk() } } }
        status("a31@x.test", viaProxyAs = "203.0.113.1").andExpect {
            status { isTooManyRequests() }
            header { exists("Retry-After") }
            jsonPath("$.message") { value("Too many requests. Please wait a few minutes and try again.") }
        }
        status("b@x.test", viaProxyAs = "203.0.113.2").andExpect { status { isOk() } }
    }

    @Test
    fun `a forged client IP without the secret is ignored, so made-up IPs don't escape the limit`() {
        // No valid secret: keyed by the connection (MockMvc's 127.0.0.1), whatever X-Client-IP says.
        repeat(30) { status("f$it@x.test", viaProxyAs = "198.51.100.$it", secret = "wrong").andExpect { status { isOk() } } }
        status("f31@x.test", viaProxyAs = "198.51.100.99", secret = "wrong").andExpect { status { isTooManyRequests() } }
    }

    @Test
    fun `per email, 3 sign-in links in 15 minutes, then 429`() {
        val user = fixtures.createUser(emailPrefix = "rl-email")
        fun ask(ip: String) = mockMvc.post("/api/auth/magic-link/request") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("email" to user.email))
            header("X-Proxy-Secret", "test-proxy-secret"); header("X-Client-IP", ip)   // different IPs: the email limit
        }
        repeat(3) { ask("192.0.2.${10 + it}").andExpect { status { isAccepted() } } }
        ask("192.0.2.20").andExpect { status { isTooManyRequests() }; header { exists("Retry-After") } }
    }
}

/** The pieces on their own. */
class RateLimiterUnitTest {

    @Test
    fun `a window allows its max, refuses the next, and resets when the time is up`() {
        val limiter = RateLimiter(enabled = true)
        var now = Instant.parse("2026-10-20T12:00:00Z")
        limiter.clock = object : Clock() {
            override fun instant() = now
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
        }
        val rule = RateLimiter.Rule("t", 2, Duration.ofMinutes(1))
        assertThat(limiter.tryAcquire(rule, "k")).isNull()
        assertThat(limiter.tryAcquire(rule, "k")).isNull()
        assertThat(limiter.tryAcquire(rule, "k")).isEqualTo(60L)
        assertThat(limiter.tryAcquire(rule, "other")).isNull()
        now = now.plusSeconds(61)
        assertThat(limiter.tryAcquire(rule, "k")).isNull()
    }

    @Test
    fun `switched off, nothing is limited`() {
        val limiter = RateLimiter(enabled = false)
        val rule = RateLimiter.Rule("t", 1, Duration.ofMinutes(1))
        repeat(5) { assertThat(limiter.tryAcquire(rule, "k")).isNull() }
    }

    @Test
    fun `without a configured secret, per-IP limits are off, with one the proxy's IP counts only with the secret`() {
        val req = MockHttpServletRequest().apply { addHeader("X-Client-IP", "203.0.113.5"); addHeader("X-Proxy-Secret", "s") }
        assertThat(ClientIpResolver("").resolve(req)).isNull()
        assertThat(ClientIpResolver("s").resolve(req)).isEqualTo("203.0.113.5")
        assertThat(ClientIpResolver("other").resolve(req)).isEqualTo("127.0.0.1")
        val direct = MockHttpServletRequest().apply { addHeader("Fly-Client-IP", "198.51.100.7") }
        assertThat(ClientIpResolver("s").resolve(direct)).isEqualTo("198.51.100.7")
    }
}
