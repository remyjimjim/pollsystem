package org.kodewerks.pollsystem.substack

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.repository.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** The Substack Stripe account's webhook (V43, decided 2026-10-28). */
@AutoConfigureMockMvc
@TestPropertySource(properties = ["app.stripe-substack.webhook-secret=whsec_substack_test"])
class StripeSubstackWebhookTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var syncLog: SubstackSyncLogRepository

    private fun sign(payload: String, secret: String = "whsec_substack_test"): String {
        val t = Instant.now().epochSecond
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")) }
        val sig = mac.doFinal("$t.$payload".toByteArray()).joinToString("") { "%02x".format(it) }
        return "t=$t,v1=$sig"
    }

    private fun send(event: Map<String, Any?>, secret: String = "whsec_substack_test") = json.writeValueAsString(event).let { body ->
        mockMvc.perform(post("/webhooks/stripe-substack").contentType(MediaType.APPLICATION_JSON)
            .header("Stripe-Signature", sign(body, secret)).content(body))
    }

    private fun invoicePaid(email: String, customer: String, periodEnd: Instant) = mapOf(
        "type" to "invoice.paid",
        "data" to mapOf("object" to mapOf("id" to "in_${System.nanoTime()}", "customer" to customer, "customer_email" to email,
            "lines" to mapOf("data" to listOf(mapOf("period" to mapOf("start" to periodEnd.minus(Duration.ofDays(30)).epochSecond, "end" to periodEnd.epochSecond)))))))

    private fun subscriptionDeleted(customer: String) = mapOf(
        "type" to "customer.subscription.deleted",
        "data" to mapOf("object" to mapOf("id" to "sub_x", "customer" to customer, "status" to "canceled")))

    @Test
    fun `a first payment makes a member paid through the period, linked to the Substack customer, and a cancellation ends it`() {
        val email = "substack-new-${System.nanoTime()}@test.local"
        val cus = "cus_${System.nanoTime()}"
        val end = Instant.now().plus(Duration.ofDays(30)).truncatedTo(ChronoUnit.SECONDS)
        send(invoicePaid(email.uppercase(), cus, end)).andExpect(status().isOk).andExpect(content().string("ok"))

        val u = users.findByEmail(email)!!
        assertThat(u.access).isEqualTo(AccessLevel.USER)
        assertThat(u.substackCustomerId).isEqualTo(cus)
        assertThat(u.paidUntil).isEqualTo(end.plus(Duration.ofDays(1)))
        assertThat(syncLog.findAllByOrderByIdDesc(org.springframework.data.domain.PageRequest.of(0, 5)).map { it.action }).contains("activated")

        send(subscriptionDeleted(cus)).andExpect(status().isOk)
        val ended = users.findByEmail(email)!!
        assertThat(ended.access).isEqualTo(AccessLevel.VIEWER)
        assertThat(ended.hasActiveSubscription).isFalse()
        assertThat(ended.substackCustomerId).isNull()
    }

    @Test
    fun `someone who also pays BallotBox is never shortened, and keeps membership when Substack ends`() {
        val user = fixtures.createUser(emailPrefix = "substack-both", paidUntil = Instant.now().plus(Duration.ofDays(200)))
        users.save(user.copy(stripeSubscriptionId = "sub_ballotbox_1"))
        val cus = "cus_${System.nanoTime()}"
        send(invoicePaid(user.email, cus, Instant.now().plus(Duration.ofDays(30)))).andExpect(status().isOk)
        val linked = users.findByEmail(user.email)!!
        assertThat(linked.substackCustomerId).isEqualTo(cus)
        assertThat(linked.paidUntil).isAfter(Instant.now().plus(Duration.ofDays(150)))

        send(subscriptionDeleted(cus)).andExpect(status().isOk)
        val after = users.findByEmail(user.email)!!
        assertThat(after.access).isEqualTo(AccessLevel.USER)
        assertThat(after.hasActiveSubscription).isTrue()
        assertThat(after.substackCustomerId).isNull()
    }

    @Test
    fun `a bad signature is refused and logged as source - stripe-substack - time - ERROR`() {
        send(invoicePaid("x@test.local", "cus_bad", Instant.now()), secret = "whsec_wrong").andExpect(status().isBadRequest)
        val last = syncLog.findAllByOrderByIdDesc(org.springframework.data.domain.PageRequest.of(0, 1)).single()
        assertThat(last.ok).isFalse()
        assertThat(last.message).matches("""source : stripe-substack : \d\d/\d\d/\d{4}:\d{1,2}:\d\d:\d\d : ERROR: Rejected a webhook with a bad signature.*""")
    }

    @Test
    fun `other events are acknowledged and ignored`() {
        send(mapOf("type" to "charge.succeeded", "data" to mapOf("object" to mapOf("id" to "ch_1")))).andExpect(content().string("ignored"))
    }

    @Test
    fun `period ends are read from older and newer Stripe API layouts`() {
        val old = json.readTree("""{"current_period_end": 1800000000, "customer": "cus_1"}""")
        val new = json.readTree("""{"items": {"data": [{"current_period_end": 1800000100}]}, "customer": {"id": "cus_2", "email": "a@b.c"}}""")
        assertThat(StripeSubstackJson.subscriptionPeriodEnd(old)).isEqualTo(Instant.ofEpochSecond(1800000000))
        assertThat(StripeSubstackJson.subscriptionPeriodEnd(new)).isEqualTo(Instant.ofEpochSecond(1800000100))
        assertThat(StripeSubstackJson.customerId(old)).isEqualTo("cus_1")
        assertThat(StripeSubstackJson.customerId(new)).isEqualTo("cus_2")
    }
}
