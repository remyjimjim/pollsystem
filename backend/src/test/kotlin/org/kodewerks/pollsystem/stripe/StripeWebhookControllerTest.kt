package org.kodewerks.pollsystem.stripe

import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.StripeEventRepository
import org.kodewerks.pollsystem.repository.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.stripe.webhook-secret=whsec_test_secret"])
class StripeWebhookControllerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var events: StripeEventRepository

    private val secret = "whsec_test_secret"

    @Test
    fun `valid signature for checkout completed links Stripe ids to existing user`() {
        val user = saveUser("paid@test.local", "+15559990001")
        val payload = """
            {"id":"evt_1","type":"checkout.session.completed","data":{"object":{
              "customer":"cus_abc","subscription":"sub_xyz",
              "customer_details":{"email":"paid@test.local"}
            }}}
        """.trimIndent()

        deliver(payload).andExpect(status().isOk)

        val updated = users.findById(user.id).get()
        assertEquals("cus_abc", updated.stripeCustomerId)
        assertEquals("sub_xyz", updated.stripeSubscriptionId)
        assertEquals(true, events.existsByStripeEventId("evt_1"))
    }

    @Test
    fun `subscription updated refreshes paid_until from current_period_end`() {
        val user = saveUser("renewer@test.local", "+15559990002")
            .copy(stripeSubscriptionId = "sub_renew")
        users.save(user)
        val periodEnd = Instant.now().plusSeconds(30 * 86400).epochSecond
        val payload = """
            {"id":"evt_2","type":"customer.subscription.updated","data":{"object":{
              "id":"sub_renew","current_period_end":$periodEnd
            }}}
        """.trimIndent()

        deliver(payload).andExpect(status().isOk)

        val updated = users.findByEmail("renewer@test.local")!!
        assertEquals(periodEnd, updated.paidUntil!!.epochSecond)
    }

    @Test
    fun `subscription deleted clears paid_until`() {
        val user = saveUser("cancelled@test.local", "+15559990003")
            .copy(
                stripeSubscriptionId = "sub_cancel",
                paidUntil = Instant.now().plusSeconds(86400)
            )
        users.save(user)
        val payload = """
            {"id":"evt_3","type":"customer.subscription.deleted","data":{"object":{
              "id":"sub_cancel"
            }}}
        """.trimIndent()

        deliver(payload).andExpect(status().isOk)

        val updated = users.findByEmail("cancelled@test.local")!!
        assertNull(updated.paidUntil)
        assertNull(updated.stripeSubscriptionId)
    }

    @Test
    fun `invalid signature is rejected with 400`() {
        val payload = """{"id":"evt_bad","type":"checkout.session.completed"}"""
        val timestamp = Instant.now().epochSecond
        mockMvc.perform(
            post("/webhooks/stripe")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Stripe-Signature", "t=$timestamp,v1=deadbeef")
                .content(payload)
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `duplicate event id is a no-op`() {
        val user = saveUser("dup@test.local", "+15559990004")
        val payload = """
            {"id":"evt_dup","type":"checkout.session.completed","data":{"object":{
              "customer":"cus_first","subscription":"sub_first",
              "customer_details":{"email":"dup@test.local"}
            }}}
        """.trimIndent()
        deliver(payload).andExpect(status().isOk)

        // Replay with the SAME event id but DIFFERENT payload — should not overwrite.
        val replay = """
            {"id":"evt_dup","type":"checkout.session.completed","data":{"object":{
              "customer":"cus_second","subscription":"sub_second",
              "customer_details":{"email":"dup@test.local"}
            }}}
        """.trimIndent()
        deliver(replay).andExpect(status().isOk)

        val updated = users.findById(user.id).get()
        assertEquals("cus_first", updated.stripeCustomerId)
    }

    private fun deliver(rawPayload: String) = mockMvc.perform(
        post("/webhooks/stripe")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Stripe-Signature", signedHeader(rawPayload))
            .content(rawPayload)
    )

    private fun signedHeader(rawPayload: String): String {
        val timestamp = Instant.now().epochSecond
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val raw = mac.doFinal("$timestamp.$rawPayload".toByteArray(Charsets.UTF_8))
        val sig = raw.joinToString("") { "%02x".format(it) }
        return "t=$timestamp,v1=$sig"
    }

    private fun saveUser(email: String, phone: String): User =
        users.save(
            User(
                email = email,
                phone = phone,
                zipcode = "90001",
                isEnabled = true
            )
        )
}
