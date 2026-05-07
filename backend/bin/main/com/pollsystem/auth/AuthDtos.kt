package com.pollsystem.auth

import com.pollsystem.model.AccessLevel
import com.pollsystem.model.User
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class LoginRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank val passcode: String
)

data class RegisterRequest(
    @field:Email @field:NotBlank val email: String,
    @field:NotBlank @field:Pattern(regexp = "^[0-9+\\-() ]{7,20}$") val phone: String,
    @field:NotBlank @field:Pattern(regexp = "^[0-9]{5}$") val zipcode: String,
    @field:NotBlank @field:Size(min = 8, max = 100) val passcode: String
)

data class UserDto(
    val id: Long,
    val email: String,
    val phone: String,
    val zipcode: String,
    val access: AccessLevel,
    val isEnabled: Boolean
) {
    companion object {
        fun from(user: User) = UserDto(
            id = user.id,
            email = user.email,
            phone = user.phone,
            zipcode = user.zipcode,
            access = user.access,
            isEnabled = user.isEnabled
        )
    }
}

data class AuthResponse(val token: String, val user: UserDto)
