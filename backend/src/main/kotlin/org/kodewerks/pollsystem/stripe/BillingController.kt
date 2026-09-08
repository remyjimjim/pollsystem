package org.kodewerks.pollsystem.stripe

import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

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
class BillingController(private val billing: BillingService) {

    /** Start a subscription Checkout Session for the current user. */
    @PostMapping("/checkout")
    fun checkout(@AuthenticationPrincipal principal: AppUserDetails): Map<String, String> =
        mapOf("url" to billing.createCheckoutSession(principal.user))

    /** Open the Customer Portal for the current user (manage/cancel subscription). */
    @PostMapping("/portal")
    fun portal(@AuthenticationPrincipal principal: AppUserDetails): Map<String, String> =
        mapOf("url" to billing.createPortalSession(principal.user))
}
