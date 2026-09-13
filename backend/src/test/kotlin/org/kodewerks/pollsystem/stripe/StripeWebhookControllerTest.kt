package org.kodewerks.pollsystem.stripe

import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.MagicLinkTokenRepository
import org.kodewerks.pollsystem.repository.StripeEventRepository
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
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.stripe.webhook-secret=whsec_test_secret"])
class StripeWebhookControllerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var events: StripeEventRepository
    @Autowired private lateinit var tokens: MagicLinkTokenRepository

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
        // Paying promotes the (default VIEWER) account to USER.
        assertEquals(AccessLevel.USER, updated.access)
        assertEquals(true, events.existsByStripeEventId("evt_1"))
    }

    @Test
    fun `checkout for unknown email provisions a paid user and issues a magic link`() {
        // No account exists for this email — the payment arrived first.
        assertNull(users.findByEmail("newpaid@test.local"))
        val payload = """
            {"id":"evt_prov","type":"checkout.session.completed","data":{"object":{
              "customer":"cus_new","subscription":"sub_new",
              "customer_details":{"email":"newpaid@test.local"}
            }}}
        """.trimIndent()

        deliver(payload).andExpect(status().isOk)

        val created = users.findByEmail("newpaid@test.local")
        assertNotNull(created); created!!
        assertEquals("cus_new", created.stripeCustomerId)
        assertEquals("sub_new", created.stripeSubscriptionId)
        assertEquals(AccessLevel.USER, created.access)
        // Provisioned from email alone — profile completed at first sign-in.
        assertNull(created.phone)
        assertNull(created.zipcode)
        // A magic link was issued so the new subscriber can get in.
        assertTrue(tokens.findAll().any { it.userId == created.id })
    }

    @Test
    fun `checkout with phone+zip metadata provisions a complete paid user`() {
        assertNull(users.findByEmail("payfirst@test.local"))
        // Pay-first register stashes the pre-collected phone + zipcode in the
        // session metadata so the account is provisioned already-complete.
        val payload = """
            {"id":"evt_meta","type":"checkout.session.completed","data":{"object":{
              "customer":"cus_meta","subscription":"sub_meta",
              "customer_details":{"email":"payfirst@test.local"},
              "metadata":{"app_phone":"+15551239876","app_zipcode":"90001"}
            }}}
        """.trimIndent()

        deliver(payload).andExpect(status().isOk)

        val created = users.findByEmail("payfirst@test.local")!!
        assertEquals("+15551239876", created.phone)
        assertEquals("90001", created.zipcode)
        assertEquals(AccessLevel.USER, created.access)
        assertTrue(created.profileComplete)
    }

    @Test
    fun `checkout metadata phone already in use falls back to an email-only account`() {
        // Someone claimed this phone between the register-form check and the
        // webhook; provisioning must not fail the UNIQUE constraint.
        saveUser("holder@test.local", "+15550001111")
        val payload = """
            {"id":"evt_race","type":"checkout.session.completed","data":{"object":{
              "customer":"cus_race","subscription":"sub_race",
              "customer_details":{"email":"raced@test.local"},
              "metadata":{"app_phone":"+15550001111","app_zipcode":"90001"}
            }}}
        """.trimIndent()

        deliver(payload).andExpect(status().isOk)

        val created = users.findByEmail("raced@test.local")!!
        // Phone dropped (kept by the other account); profile completed later.
        assertNull(created.phone)
        assertNull(created.zipcode)
        assertEquals(AccessLevel.USER, created.access)
    }

    @Test
    fun `subscription created sets paid_until for a first-time subscriber`() {
        // Stripe fires customer.subscription.created (not .updated) for a brand-new
        // subscription; without handling it, a first-time subscriber's paid_until
        // never gets set and they stay LAPSED despite paying.
        val user = saveUser("firsttimer@test.local", "+15559990007")
            .copy(stripeSubscriptionId = "sub_created")
        users.save(user)
        val periodEnd = Instant.now().plusSeconds(30 * 86400).epochSecond
        val payload = """
            {"id":"evt_created","type":"customer.subscription.created","data":{"object":{
              "id":"sub_created","current_period_end":$periodEnd
            }}}
        """.trimIndent()

        deliver(payload).andExpect(status().isOk)

        val updated = users.findByEmail("firsttimer@test.local")!!
        assertEquals(periodEnd, updated.paidUntil!!.epochSecond)
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
                access = AccessLevel.CREATOR,
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
        // A lapsed non-SUPER account is demoted to VIEWER.
        assertEquals(AccessLevel.VIEWER, updated.access)
    }

    @Test
    fun `subscription deleted does not demote SUPER`() {
        val user = saveUser("boss@test.local", "+15559990009")
            .copy(
                access = AccessLevel.SUPER,
                stripeSubscriptionId = "sub_super",
                paidUntil = Instant.now().plusSeconds(86400)
            )
        users.save(user)
        val payload = """
            {"id":"evt_super","type":"customer.subscription.deleted","data":{"object":{
              "id":"sub_super"
            }}}
        """.trimIndent()

        deliver(payload).andExpect(status().isOk)

        val updated = users.findByEmail("boss@test.local")!!
        assertNull(updated.paidUntil)
        assertEquals(AccessLevel.SUPER, updated.access)
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

    @Test
    fun `subscription updated reads current_period_end from items (2025 API)`() {
        val user = saveUser("itemrenew@test.local", "+15559990005")
            .copy(stripeSubscriptionId = "sub_item")
        users.save(user)
        val periodEnd = Instant.now().plusSeconds(30 * 86400).epochSecond
        // Newer Stripe API versions drop current_period_end from the subscription
        // object and expose it per subscription item instead.
        val payload = """
            {"id":"evt_item","type":"customer.subscription.updated","data":{"object":{
              "id":"sub_item",
              "items":{"data":[{"current_period_end":$periodEnd}]}
            }}}
        """.trimIndent()

        deliver(payload).andExpect(status().isOk)

        val updated = users.findByEmail("itemrenew@test.local")!!
        assertEquals(periodEnd, updated.paidUntil!!.epochSecond)
    }

    @Test
    fun `invoice paid uses latest line period and parent subscription ref`() {
        val user = saveUser("invrenew@test.local", "+15559990006")
            .copy(stripeSubscriptionId = "sub_inv")
        users.save(user)
        val past = Instant.now().minusSeconds(5 * 86400).epochSecond    // proration credit line
        val newEnd = Instant.now().plusSeconds(30 * 86400).epochSecond  // subscription renewal line
        // 2025-03-31.basil+ shape: subscription id under parent.subscription_details,
        // and multiple lines where the renewal is the latest period end.
        val payload = """
            {"id":"evt_inv","type":"invoice.paid","data":{"object":{
              "parent":{"subscription_details":{"subscription":"sub_inv"}},
              "lines":{"data":[
                {"period":{"end":$past}},
                {"period":{"end":$newEnd}}
              ]}
            }}}
        """.trimIndent()

        deliver(payload).andExpect(status().isOk)

        val updated = users.findByEmail("invrenew@test.local")!!
        assertEquals(newEnd, updated.paidUntil!!.epochSecond)
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
