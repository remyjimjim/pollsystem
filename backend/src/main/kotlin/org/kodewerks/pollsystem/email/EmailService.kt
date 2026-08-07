package org.kodewerks.pollsystem.email

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service

interface EmailService {
    fun send(to: String, subject: String, body: String)
}

/**
 * Sends mail via Spring's `JavaMailSender` when one is configured. Falls back
 * to a log line when it isn't — so test contexts and bring-up environments
 * don't fail just because SMTP is missing. Send failures are logged but not
 * re-thrown, mirroring `MagicLinkEmailer`: a transient SMTP outage shouldn't
 * fail the user-facing request that triggered the email.
 */
@Service
class SmtpEmailService(
    private val mailProvider: ObjectProvider<JavaMailSender>,
    // Verified sender (Resend rejects unverified From). Default matches the
    // verified Resend sending subdomain; override via MAIL_FROM in prod.
    @Value("\${MAIL_FROM:no-reply@contact.surveysays.buzz}") private val from: String,
) : EmailService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(to: String, subject: String, body: String) {
        val mail = mailProvider.ifAvailable
        if (mail == null) {
            log.info("[EMAIL stub] to={} subject={}\n{}", to, subject, body)
            return
        }
        // Explicit MimeMessage: Resend's SMTP rejects the message JavaMailSender
        // derives from a SimpleMailMessage ("550 ... from field: undefined").
        // See MagicLinkEmailer for the full write-up.
        val mime = mail.createMimeMessage()
        MimeMessageHelper(mime, false, "UTF-8").apply {
            setFrom(from)
            setTo(to)
            setSubject(subject)
            setText(body)
        }
        try {
            mail.send(mime)
        } catch (e: Exception) {
            log.warn("Email send failed (to={} subject={}): {}", to, subject, e.message)
        }
    }
}
