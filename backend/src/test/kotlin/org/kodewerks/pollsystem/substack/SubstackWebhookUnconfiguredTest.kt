package org.kodewerks.pollsystem.substack

import com.fasterxml.jackson.databind.ObjectMapper
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * With no `app.substack.webhook-secret` configured (the default), the endpoint
 * is disabled and returns 503 — a forged call can't drive membership before
 * Substack is wired up.
 */
@AutoConfigureMockMvc
class SubstackWebhookUnconfiguredTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper

    @Test
    fun `webhook is disabled (503) when no secret is configured`() {
        mockMvc.perform(
            post("/webhooks/substack")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Webhook-Secret", "anything")
                .content(json.writeValueAsString(mapOf("email" to "a@test.local", "event" to "subscribed")))
        ).andExpect(status().isServiceUnavailable)
    }
}
