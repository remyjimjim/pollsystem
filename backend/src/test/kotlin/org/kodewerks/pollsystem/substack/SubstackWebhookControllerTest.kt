package org.kodewerks.pollsystem.substack

import com.fasterxml.jackson.databind.ObjectMapper
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.MagicLinkTokenRepository
import org.kodewerks.pollsystem.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.substack.webhook-secret=sub_test_secret"])
class SubstackWebhookControllerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var tokens: MagicLinkTokenRepository

    private val secret = "sub_test_secret"

    private fun deliver(email: String, event: String, withSecret: String? = secret) =
        mockMvc.perform(
            post("/webhooks/substack")
                .contentType(MediaType.APPLICATION_JSON)
                .apply { withSecret?.let { header("X-Webhook-Secret", it) } }
                .content(json.writeValueAsString(mapOf("email" to email, "event" to event)))
        )

    @Test
    fun `subscribed for an unknown email provisions a paid member and issues a magic link`() {
        assertNull(users.findByEmail("newsub@test.local"))

        deliver("newsub@test.local", "subscribed").andExpect(status().isOk)

        val created = users.findByEmail("newsub@test.local")
        assertNotNull(created); created!!
        assertEquals(AccessLevel.USER, created.access)
        assertTrue(created.hasActiveSubscription)
        // Email only — phone/zip collected at first sign-in.
        assertNull(created.phone)
        assertTrue(tokens.findAll().any { it.userId == created.id })
    }

    @Test
    fun `subscribed reactivates a lapsed VIEWER back to USER`() {
        users.save(User(email = "back@test.local", access = AccessLevel.VIEWER, isEnabled = true))

        deliver("back@test.local", "renewed").andExpect(status().isOk)

        val updated = users.findByEmail("back@test.local")!!
        assertEquals(AccessLevel.USER, updated.access)
        assertTrue(updated.hasActiveSubscription)
    }

    @Test
    fun `unsubscribed drops membership and demotes a non-SUPER to VIEWER`() {
        users.save(
            User(
                email = "gone@test.local",
                access = AccessLevel.CREATOR,
                isEnabled = true,
                paidUntil = Instant.now().plusSeconds(86400)
            )
        )

        deliver("gone@test.local", "unsubscribed").andExpect(status().isOk)

        val updated = users.findByEmail("gone@test.local")!!
        assertNull(updated.paidUntil)
        assertEquals(AccessLevel.VIEWER, updated.access)
    }

    @Test
    fun `a bad secret is rejected with 401`() {
        deliver("x@test.local", "subscribed", withSecret = "wrong").andExpect(status().isUnauthorized)
        assertNull(users.findByEmail("x@test.local"))
    }

    @Test
    fun `a missing secret is rejected with 401`() {
        deliver("y@test.local", "subscribed", withSecret = null).andExpect(status().isUnauthorized)
    }

    @Test
    fun `an unhandled event is acknowledged but changes nothing`() {
        deliver("z@test.local", "profile.updated").andExpect(status().isOk)
        assertNull(users.findByEmail("z@test.local"))
    }

    @Test
    fun `a malformed email is rejected with 400`() {
        deliver("not-an-email", "subscribed").andExpect(status().isBadRequest)
    }
}
