package org.kodewerks.pollsystem.payment

import org.kodewerks.pollsystem.model.User
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * Stands in for Stripe in tests that need billing calls to succeed (or fail on
 * demand), and records them. Use with @Import(FakePayments::class); call
 * [reset] before each test, as the bean is shared by every such test class.
 */
class FakePaymentProvider : PaymentProvider {
    val calls = mutableListOf<String>()
    var failCancelNow = false

    fun reset() { calls.clear(); failCancelNow = false }

    override fun createCheckoutSession(user: User) = "https://checkout.test/${user.id}"
    override fun createGuestCheckoutSession(email: String, phone: String, zipcode: String) = "https://checkout.test/guest"
    override fun createPortalSession(user: User) = "https://portal.test/${user.id}"

    override fun setCancelAtPeriodEnd(user: User, cancel: Boolean) {
        // Like Stripe: nothing to change without a subscription.
        if (user.stripeSubscriptionId.isNullOrBlank()) throw ResponseStatusException(HttpStatus.CONFLICT, "No subscription")
        calls += "cancelAtPeriodEnd(${user.id}, $cancel)"
    }

    override fun cancelNow(user: User) {
        if (failCancelNow) throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not cancel the subscription")
        calls += "cancelNow(${user.id})"
    }
}

@TestConfiguration
class FakePayments {
    @Bean @Primary
    fun fakePaymentProvider() = FakePaymentProvider()
}
