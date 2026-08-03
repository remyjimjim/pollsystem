package org.kodewerks.pollsystem.auth

import org.kodewerks.pollsystem.model.User
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

/**
 * Sends the magic-link email. Send failures are logged but not re-thrown — a
 * transient SMTP outage shouldn't fail the user-facing request endpoint;
 * the user can request another link. Likewise, a deployment without SMTP
 * configured (no `JavaMailSender` bean) is treated as "no-op delivery";
 * useful for tests and bring-up environments.
 */
@Service
class MagicLinkEmailer(
    private val mailProvider: ObjectProvider<JavaMailSender>,
    private val magicLinks: MagicLinkService,
    // Verified sender for the provider (Resend rejects unverified From). The
    // default matches the verified Resend sending subdomain; override with
    // MAIL_FROM in prod (e.g. login@contact.surveysays.buzz). Local Mailpit
    // accepts anything, so the default suffices there too.
    @Value("\${MAIL_FROM:no-reply@contact.surveysays.buzz}") private val from: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun send(user: User, rawToken: String) {
        val mail = mailProvider.ifAvailable
        if (mail == null) {
            log.warn("JavaMailSender not configured; magic-link email not sent for user {}", user.id)
            return
        }
        val url = magicLinks.buildRedeemUrl(rawToken)
        val msg = SimpleMailMessage().apply {
            setFrom(from)
            setTo(user.email)
            setSubject("Your sign-in link")
            setText(
                """
                Click the link below to sign in. It expires in 15 minutes and can be used once.

                $url

                If you didn't request this, you can ignore this email.
                """.trimIndent()
            )
        }
        try {
            mail.send(msg)
        } catch (e: Exception) {
            log.warn("Magic-link email failed for user {}: {}", user.id, e.message)
        }
    }
}
