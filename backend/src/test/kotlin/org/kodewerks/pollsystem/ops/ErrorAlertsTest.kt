package org.kodewerks.pollsystem.ops

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.email.EmailService
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class ErrorAlertsTest {

    private class Sent(val to: String, val subject: String, val body: String)
    private val sent = mutableListOf<Sent>()
    private val mail = object : EmailService {
        override fun send(to: String, subject: String, body: String) { sent += Sent(to, subject, body) }
    }
    private var now = Instant.parse("2026-10-21T12:00:00Z")
    private val alerts = ErrorAlerts(mail, "owner@example.test", "pollsystem-backend").also {
        it.clock = object : Clock() {
            override fun instant() = now
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: ZoneId?) = this
        }
    }
    private val loggers = LoggerContext()

    private fun log(level: Level, logger: String, msg: String, t: Throwable? = null) =
        alerts.record(LoggingEvent("x", loggers.getLogger(logger), level, msg, t, null))

    @Test
    fun `errors are sent as one digest, warnings are not`() {
        log(Level.WARN, "org.kodewerks.pollsystem.poll.X", "just a warning")
        log(Level.ERROR, "org.kodewerks.pollsystem.poll.X", "first failure")
        log(Level.ERROR, "org.hibernate.Y", "second failure", IllegalStateException("boom"))
        alerts.flush()

        assertThat(sent).hasSize(1)
        assertThat(sent[0].to).isEqualTo("owner@example.test")
        assertThat(sent[0].subject).isEqualTo("[pollsystem-backend] 2 errors")
        assertThat(sent[0].body).contains("first failure", "second failure", "IllegalStateException: boom", "flyctl logs -a pollsystem-backend")
        assertThat(sent[0].body).doesNotContain("just a warning")
    }

    @Test
    fun `at most one digest every 15 minutes, and nothing is lost meanwhile`() {
        log(Level.ERROR, "a.B", "one")
        alerts.flush()
        log(Level.ERROR, "a.B", "two")
        now = now.plusSeconds(5 * 60)
        alerts.flush()
        assertThat(sent).hasSize(1)                      // too soon

        now = now.plusSeconds(10 * 60)
        alerts.flush()
        assertThat(sent).hasSize(2)
        assertThat(sent[1].subject).isEqualTo("[pollsystem-backend] 1 error")
        assertThat(sent[1].body).contains("two")
        alerts.flush()
        assertThat(sent).hasSize(2)                      // nothing new, nothing sent
    }

    @Test
    fun `a flood is capped, and the digest says how many more there were`() {
        repeat(ErrorAlerts.MAX_KEPT + 5) { log(Level.ERROR, "a.B", "e$it") }
        alerts.flush()
        assertThat(sent.single().subject).isEqualTo("[pollsystem-backend] ${ErrorAlerts.MAX_KEPT + 5} errors")
        assertThat(sent.single().body).contains("…and 5 more not shown.")
    }

    @Test
    fun `mail failures are ignored, so a mail outage can't loop`() {
        log(Level.ERROR, "org.kodewerks.pollsystem.email.SmtpEmailService", "send failed")
        log(Level.ERROR, "org.springframework.mail.X", "smtp down")
        alerts.flush()
        assertThat(sent).isEmpty()
    }

    @Test
    fun `off without an address`() {
        assertThat(ErrorAlerts(mail, "", "x").enabled).isFalse()
        assertThat(alerts.enabled).isTrue()
    }
}
