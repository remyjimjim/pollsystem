package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * Guards poll participation (submitting a response). Two requirements:
 *
 *  1. **Complete profile** (phone + zipcode) — the zipcode drives geo-filtering
 *     and k-anonymity of results, so a response with no location can't be counted.
 *  2. **Active paid membership** — a registered USER only becomes a *participating*
 *     member by paying (`paidUntil` in the future); paying is what turns a viewer
 *     into a participant. Only **SUPER** is exempt — USER, CREATOR and ADMIN all
 *     need an active subscription (creators pay a reduced rate; see the coupon
 *     follow-up). A lapsed subscription demotes the account to VIEWER (webhook).
 *
 * Viewing stays open to everyone; this only gates submission. The frontend
 * mirrors both checks; this is the server-side backstop. The subscription
 * failure returns 402 (Payment Required) so the client can prompt to subscribe.
 */
fun requireParticipation(principal: AppUserDetails) {
    val user = principal.user
    if (!user.profileComplete) {
        throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Complete your profile (phone + zipcode) before submitting a response",
        )
    }
    if (user.access != AccessLevel.SUPER && !user.hasActiveSubscription) {
        throw ResponseStatusException(
            HttpStatus.PAYMENT_REQUIRED,
            "An active subscription is required to participate",
        )
    }
}
