package org.kodewerks.pollsystem.stripe

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.poll.PollMilestoneTest
import org.kodewerks.pollsystem.repository.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** A second paid checkout for an account that's already a member (2026-10-24: a member was charged twice). */
@AutoConfigureMockMvc
@Import(PollMilestoneTest.RecordingEmailConfig::class)
@TestPropertySource(properties = ["app.stripe.webhook-secret=whsec_test_secret"])
class DuplicatePaymentTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var mail: PollMilestoneTest.RecordingEmailService

    @BeforeEach fun clear() = mail.clear()

    private fun member(email: String, sub: String, paidUntil: Instant?) = users.save(User(
        email = email, phone = "+1555${(1000000..9999999).random()}", zipcode = "90001", isEnabled = true,
        access = AccessLevel.USER, stripeCustomerId = "cus_first", stripeSubscriptionId = sub, paidUntil = paidUntil
    ))

    private fun checkout(event: String, email: String, sub: String) = mockMvc.perform(
        post("/webhooks/stripe").contentType(MediaType.APPLICATION_JSON)
            .header("Stripe-Signature", signed(payload(event, email, sub))).content(payload(event, email, sub))
    )

    private fun payload(event: String, email: String, sub: String) =
        """{"id":"$event","type":"checkout.session.completed","data":{"object":{"customer":"cus_second","subscription":"$sub","customer_details":{"email":"$email"}}}}"""

    private fun signed(raw: String): String {
        val ts = Instant.now().epochSecond
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec("whsec_test_secret".toByteArray(), "HmacSHA256")) }
        return "t=$ts,v1=" + mac.doFinal("$ts.$raw".toByteArray()).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `a second payment while already a member keeps the first subscription and tells the member`() {
        val u = member("dup-member@test.local", "sub_first", Instant.now().plus(20, ChronoUnit.DAYS))

        checkout("evt_dup_1", "dup-member@test.local", "sub_second").andExpect(status().isOk)

        val after = users.findById(u.id).get()
        assertThat(after.stripeSubscriptionId).isEqualTo("sub_first")
        assertThat(after.stripeCustomerId).isEqualTo("cus_first")
        val sent = mail.sent.single { it.to == "dup-member@test.local" }
        assertThat(sent.subject).isEqualTo("You were already a BallotBox member")
        // Billing is unconfigured in tests, so the automatic refund can't run: the member is told it's coming.
        assertThat(sent.body).contains("We'll cancel the second subscription and refund that payment shortly.")
    }

    @Test
    fun `a lapsed member paying again is a normal renewal, not a duplicate`() {
        val u = member("dup-lapsed@test.local", "sub_old", Instant.now().minus(3, ChronoUnit.DAYS))

        checkout("evt_dup_2", "dup-lapsed@test.local", "sub_new").andExpect(status().isOk)

        assertThat(users.findById(u.id).get().stripeSubscriptionId).isEqualTo("sub_new")
        assertThat(mail.sent.none { it.to == "dup-lapsed@test.local" }).isTrue()
    }
}
