package org.kodewerks.pollsystem.auth

import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.User
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.Instant

/**
 * Sign-in request. `email` is always required. `phone` and `zipcode` are only
 * required when the email is new (the request would provision a new user) —
 * returning users just enter their email. The controller enforces this; the
 * DTO only enforces format when the field is present.
 */
data class MagicLinkRequest(
    @field:Email @field:NotBlank val email: String,
    @field:Pattern(regexp = "^[0-9+\\-() ]{7,20}$") val phone: String? = null,
    @field:Pattern(regexp = "^[0-9]{5}$") val zipcode: String? = null
)

data class MagicLinkRedeemRequest(
    @field:NotBlank val token: String
)

/**
 * Supplies the phone + zipcode a payment-first user was provisioned without.
 * Both required and format-validated (as in MagicLinkRequest); the controller
 * additionally checks phone uniqueness and that the zipcode is a real one.
 */
data class CompleteProfileRequest(
    @field:Pattern(regexp = "^[0-9+\\-() ]{7,20}$") @field:NotBlank val phone: String,
    @field:Pattern(regexp = "^[0-9]{5}$") @field:NotBlank val zipcode: String
)

data class UserDto(
    val id: Long,
    val email: String,
    val phone: String?,
    val zipcode: String?,
    val access: AccessLevel,
    val isEnabled: Boolean,
    val paidUntil: Instant?,
    val profileComplete: Boolean
) {
    companion object {
        fun from(user: User) = UserDto(
            id = user.id,
            email = user.email,
            phone = user.phone,
            zipcode = user.zipcode,
            access = user.access,
            isEnabled = user.isEnabled,
            paidUntil = user.paidUntil,
            profileComplete = user.profileComplete
        )
    }
}

data class AuthResponse(val token: String, val user: UserDto)
