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

data class UserDto(
    val id: Long,
    val email: String,
    val phone: String,
    val zipcode: String,
    val access: AccessLevel,
    val isEnabled: Boolean,
    val paidUntil: Instant?
) {
    companion object {
        fun from(user: User) = UserDto(
            id = user.id,
            email = user.email,
            phone = user.phone,
            zipcode = user.zipcode,
            access = user.access,
            isEnabled = user.isEnabled,
            paidUntil = user.paidUntil
        )
    }
}

data class AuthResponse(val token: String, val user: UserDto)
