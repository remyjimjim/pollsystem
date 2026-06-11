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
 *   application.yml): mail goes to a Mailpit container at
 *   localhost:1025. No auth, no STARTTLS — Mailpit accepts anything and
 *   surfaces it at http://localhost:8025 for inspection.
 * - Any other profile: SendGrid SMTP relay. `SENDGRID_API_KEY` must be
 *   provided in the environment; if it's missing the bean still builds
 *   but sends will fail at runtime, which `MagicLinkEmailer` and
 *   `SmtpEmailService` log without throwing.
 */
@Configuration
class MailConfig {

    @Bean
    @Profile("local")
    fun mailpitMailSender(): JavaMailSender = JavaMailSenderImpl().apply {
        host = "localhost"
        port = 1025
    }

    @Bean
    @Profile("!local")
    fun sendgridMailSender(
        @Value("\${SENDGRID_API_KEY:}") apiKey: String
    ): JavaMailSender = JavaMailSenderImpl().apply {
        host = "smtp.sendgrid.net"
        port = 587
        username = "apikey"
        password = apiKey
        javaMailProperties = Properties().apply {
            setProperty("mail.smtp.auth", "true")
            setProperty("mail.smtp.starttls.enable", "true")
        }
    }
}
