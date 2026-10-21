package org.kodewerks.pollsystem.ops

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.AppenderBase
import org.kodewerks.pollsystem.email.EmailService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Emails the owner when the backend logs errors (launch agenda, monitoring;
 * decided 2026-10-21). Every ERROR log line is collected and sent as one
 * digest, at most every 15 minutes, so a burst is one email, not hundreds.
 * Off unless app.alerts.email (ALERT_EMAIL) is set.
 *
 * Errors from the mail code itself are ignored: if mail is down, alerting
 * about it by mail would only loop.
 */
@Component
class ErrorAlerts(
    private val email: EmailService,
    @Value("\${app.alerts.email:}") private val to: String,
    @Value("\${app.alerts.label:pollsystem}") private val label: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    var clock: Clock = Clock.systemUTC()

    data class Entry(val at: Instant, val logger: String, val message: String)

    private val pending = ConcurrentLinkedQueue<Entry>()
    private val kept = AtomicInteger()
    private val dropped = AtomicInteger()
    private var lastSent: Instant? = null

    val enabled: Boolean get() = to.isNotBlank()

    @EventListener(ApplicationReadyEvent::class)
    fun attach() {
        if (!enabled) {
            log.info("Error alerts off (set ALERT_EMAIL to email a digest of errors)")
            return
        }
        val context = LoggerFactory.getILoggerFactory() as LoggerContext
        val appender = object : AppenderBase<ILoggingEvent>() {
            override fun append(event: ILoggingEvent) = record(event)
        }.apply { this.context = context; name = "ERROR_ALERTS"; start() }
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender)
        log.info("Error alerts on: a digest goes to the alert address at most every {} minutes", EVERY.toMinutes())
    }

    /** Collects one log event, if it's an error worth sending. */
    fun record(event: ILoggingEvent) {
        if (!event.level.isGreaterOrEqual(Level.ERROR)) return
        val name = event.loggerName
        if (IGNORED.any { name.startsWith(it) }) return
        if (kept.incrementAndGet() > MAX_KEPT) { kept.decrementAndGet(); dropped.incrementAndGet(); return }
        val trace = event.throwableProxy?.let { "\n" + ThrowableProxyUtil.asString(it).lines().take(TRACE_LINES).joinToString("\n") } ?: ""
        pending += Entry(Instant.ofEpochMilli(event.timeStamp), name, event.formattedMessage + trace)
    }

    /** Sends what has been collected, unless a digest went out less than 15 minutes ago. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    fun flush() {
        if (pending.isEmpty()) return
        val now = clock.instant()
        lastSent?.let { if (Duration.between(it, now) < EVERY) return }
        val entries = generateSequence { pending.poll() }.toList()
        kept.addAndGet(-entries.size)
        val more = dropped.getAndSet(0)
        lastSent = now
        val total = entries.size + more
        val body = buildString {
            appendLine("$total error${if (total == 1) "" else "s"} logged by $label since the last alert.")
            appendLine("Full logs: flyctl logs -a $label")
            entries.forEach { appendLine(); appendLine("${it.at}  ${it.logger}"); appendLine(it.message) }
            if (more > 0) { appendLine(); appendLine("…and $more more not shown.") }
        }
        email.send(to, "[$label] $total error${if (total == 1) "" else "s"}", body)
    }

    companion object {
        val EVERY: Duration = Duration.ofMinutes(15)
        const val MAX_KEPT = 30
        const val TRACE_LINES = 12
        private val IGNORED = listOf(ErrorAlerts::class.java.name, "org.kodewerks.pollsystem.email", "org.springframework.mail", "jakarta.mail")
    }
}
