package org.kodewerks.pollsystem.email

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import java.util.Properties

/**
 * Profile-conditional `JavaMailSender` wiring. Defining both beans
 * explicitly displaces Spring Boot's mail auto-configuration, so the
 * runtime choice between SMTP backends is grep-able in code instead of
 * a property merge across multiple application-*.yml files.
 *
 * - `local` profile (the dev default; see `spring.profiles.default` in
 *   application.yml): mail goes to a Mailpit container, by default at
 *   localhost:1025 (override with `MAIL_SMTP_HOST` / `MAIL_SMTP_PORT` — e.g.
 *   the backend running inside docker-compose reaches Mailpit at `mailpit:1025`
 *   over the compose network). No auth, no STARTTLS — Mailpit accepts anything
 *   and surfaces it at http://localhost:8025 for inspection.
 * - Any other profile: a generic authenticated SMTP relay, **defaulting to
 *   Resend** (`smtp.resend.com:587`, username `resend`, password = the Resend
 *   API key in `RESEND_API_KEY`). Every field is env-overridable, so switching
 *   providers (SES, SendGrid, …) needs no code change — set `MAIL_SMTP_HOST` /
 *   `MAIL_SMTP_PORT` / `MAIL_SMTP_USERNAME` / `MAIL_SMTP_PASSWORD`. If the
 *   password is missing the bean still builds but sends fail at runtime, which
 *   `MagicLinkEmailer` and `SmtpEmailService` log without throwing.
 */
@Configuration
class MailConfig {

    @Bean
    @Profile("local")
    fun mailpitMailSender(
        // Default to localhost:1025 (Mailpit on the host, for `gradlew bootRun`);
        // the containerized backend sets MAIL_SMTP_HOST=mailpit to reach it over
        // the compose network. Same env vars as the non-local SMTP bean.
        @Value("\${MAIL_SMTP_HOST:localhost}") mailHost: String,
        @Value("\${MAIL_SMTP_PORT:1025}") mailPort: Int,
    ): JavaMailSender = JavaMailSenderImpl().apply {
        host = mailHost
        port = mailPort
    }

    @Bean
    @Profile("!local")
    fun smtpMailSender(
        @Value("\${MAIL_SMTP_HOST:smtp.resend.com}") smtpHost: String,
        @Value("\${MAIL_SMTP_PORT:587}") smtpPort: Int,
        @Value("\${MAIL_SMTP_USERNAME:resend}") smtpUsername: String,
        // Falls back to RESEND_API_KEY when MAIL_SMTP_PASSWORD isn't set.
        @Value("\${MAIL_SMTP_PASSWORD:\${RESEND_API_KEY:}}") smtpPassword: String,
        // Envelope reverse-path (SMTP `MAIL FROM`). Must match the sender the
        // mailers use (see MagicLinkEmailer / SmtpEmailService). Resend rejects
        // an empty envelope sender with "550 Invalid `from` field" — JavaMail
        // leaves it empty by default, so set it explicitly. (Mailpit doesn't
        // care, which is why this only surfaced against Resend in prod.)
        @Value("\${MAIL_FROM:no-reply@contact.surveysays.buzz}") mailFrom: String,
    ): JavaMailSender = JavaMailSenderImpl().apply {
        host = smtpHost
        port = smtpPort
        username = smtpUsername
        password = smtpPassword
        javaMailProperties = Properties().apply {
            setProperty("mail.smtp.auth", "true")
            setProperty("mail.smtp.starttls.enable", "true")
            setProperty("mail.smtp.from", mailFrom)
        }
    }
}
