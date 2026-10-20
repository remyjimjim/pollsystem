package org.kodewerks.pollsystem.stripe

import org.kodewerks.pollsystem.auth.UserDto
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.payment.PaymentProvider
import org.kodewerks.pollsystem.repository.UserRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * Billing endpoints for the logged-in user. Both require authentication
 * (SecurityConfig gates every `/api` route via anyRequest().authenticated())
 * and return `{ "url": … }` for the frontend to redirect to.
 *
 * The principal's User is loaded fresh per request by the JWT filter, so
 * stripeCustomerId reflects anything the webhook has already linked.
 */
@RestController
@RequestMapping("/api/billing")
class BillingController(private val billing: PaymentProvider, private val users: UserRepository) {

    /** Start a subscription Checkout Session for the current user. */
    @PostMapping("/checkout")
    fun checkout(@AuthenticationPrincipal principal: AppUserDetails): Map<String, String> =
        mapOf("url" to billing.createCheckoutSession(principal.user))

    /** Open the Customer Portal for the current user (manage/cancel subscription). */
    @PostMapping("/portal")
    fun portal(@AuthenticationPrincipal principal: AppUserDetails): Map<String, String> =
        mapOf("url" to billing.createPortalSession(principal.user))

    /**
     * Cancel the membership: it runs to the end of the month already paid for
     * and isn't renewed. Afterwards the member can still sign in and view
     * results, and renews from the home page (decided 2026-10-20).
     */
    @PostMapping("/cancel")
    fun cancel(@AuthenticationPrincipal principal: AppUserDetails): UserDto = setCancelling(principal.user, true)

    /** Undo a cancellation while the paid month is still running. */
    @PostMapping("/resume")
    fun resume(@AuthenticationPrincipal principal: AppUserDetails): UserDto = setCancelling(principal.user, false)

    private fun setCancelling(user: User, cancel: Boolean): UserDto {
        if (!user.hasActiveSubscription) throw ResponseStatusException(HttpStatus.CONFLICT, "No active membership")
        billing.setCancelAtPeriodEnd(user, cancel)   // Stripe: 409 without a subscription
        // Recorded now so the page updates at once; Stripe's webhook confirms it.
        return UserDto.from(users.save(user.copy(cancelAtPeriodEnd = cancel)))
    }
}
