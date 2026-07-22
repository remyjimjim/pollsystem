package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * Guards poll participation. A payment-first user is provisioned with only an
 * email and must complete their profile (phone + zipcode) before submitting any
 * response — the zipcode drives geo-filtering and k-anonymity of results, so a
 * response with no location can't be counted. Viewing stays open; this only
 * gates submission. The frontend routes incomplete users to a completion step,
 * so this is the server-side backstop.
 */
fun requireCompleteProfile(principal: AppUserDetails) {
    if (!principal.user.profileComplete) {
        throw ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Complete your profile (phone + zipcode) before submitting a response",
        )
    }
}
