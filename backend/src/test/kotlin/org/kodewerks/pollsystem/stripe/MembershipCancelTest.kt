package org.kodewerks.pollsystem.stripe

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.payment.FakePaymentProvider
import org.kodewerks.pollsystem.payment.FakePayments
import org.kodewerks.pollsystem.repository.UserRepository
import org.kodewerks.pollsystem.security.JwtTokenProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

/** Cancel / Keep my membership (decided 2026-10-20: ends at the end of the paid month). */
@AutoConfigureMockMvc
@Import(FakePayments::class)
class MembershipCancelTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var tokens: JwtTokenProvider
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var payments: FakePaymentProvider

    @BeforeEach fun reset() = payments.reset()

    private fun member(): User = users.save(fixtures.createUser(emailPrefix = "cancel").copy(stripeSubscriptionId = "sub_cancel_test"))
    private fun bearer(u: User) = "Bearer ${tokens.generateToken(u.id, u.email)}"

    @Test
    fun `cancel sets the subscription to end with the paid month, and keep undoes it`() {
        val u = member()
        mockMvc.post("/api/billing/cancel") { header("Authorization", bearer(u)) }.andExpect {
            status { isOk() }
            jsonPath("$.cancelAtPeriodEnd") { value(true) }
            jsonPath("$.paidUntil") { exists() }
        }
        assertThat(users.findById(u.id).get().cancelAtPeriodEnd).isTrue()
        assertThat(users.findById(u.id).get().hasActiveSubscription).isTrue()   // still a member until then

        mockMvc.post("/api/billing/resume") { header("Authorization", bearer(u)) }.andExpect {
            status { isOk() }
            jsonPath("$.cancelAtPeriodEnd") { value(false) }
        }
        assertThat(users.findById(u.id).get().cancelAtPeriodEnd).isFalse()
        assertThat(payments.calls).containsExactly("cancelAtPeriodEnd(${u.id}, true)", "cancelAtPeriodEnd(${u.id}, false)")
    }

    @Test
    fun `nothing to cancel without an active membership`() {
        val lapsed = users.save(fixtures.createUser(emailPrefix = "lapsed", paidUntil = null).copy(stripeSubscriptionId = "sub_old"))
        mockMvc.post("/api/billing/cancel") { header("Authorization", bearer(lapsed)) }.andExpect { status { isConflict() } }
        val noSub = fixtures.createUser(emailPrefix = "nosub")
        mockMvc.post("/api/billing/cancel") { header("Authorization", bearer(noSub)) }.andExpect { status { isConflict() } }
        assertThat(payments.calls).isEmpty()
    }

    @Test
    fun `cancel requires signing in`() {
        mockMvc.post("/api/billing/cancel").andExpect { status { isUnauthorized() } }
    }
}
