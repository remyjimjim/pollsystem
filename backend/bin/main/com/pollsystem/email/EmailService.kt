package com.pollsystem.email

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

interface EmailService {
    fun send(to: String, subject: String, body: String)
}

@Service
class LoggingEmailService : EmailService {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun send(to: String, subject: String, body: String) {
        log.info("[EMAIL] to={} subject={}\n{}", to, subject, body)
    }
}
