package com.pollsystem.auth

import com.pollsystem.model.AccessLevel
import com.pollsystem.model.User
import com.pollsystem.repository.UserRepository
import com.pollsystem.security.AppUserDetails
import com.pollsystem.security.JwtTokenProvider
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val users: UserRepository,
    private val tokenProvider: JwtTokenProvider,
    private val magicLinks: MagicLinkService,
    private val emailer: MagicLinkEmailer
) {

    /**
     * Request a magic-link sign-in. If no user exists for the email, a new
     * USER-tier account is provisioned. Email and phone are formatting-only
     * validated (see DTO); we do not send a verification SMS or check MX
     * records. Always returns 202 to avoid leaking which emails are registered.
     */
    @PostMapping("/magic-link/request")
    fun requestMagicLink(@Valid @RequestBody req: MagicLinkRequest): ResponseEntity<Void> {
        val user = users.findByEmail(req.email)
            ?: provision(req)
        val rawToken = magicLinks.issueToken(user)
        emailer.send(user, rawToken)
        return ResponseEntity.accepted().build()
    }

    @PostMapping("/magic-link/redeem")
    fun redeemMagicLink(@Valid @RequestBody req: MagicLinkRedeemRequest): AuthResponse {
        val userId = magicLinks.redeem(req.token)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token")
        val user = users.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token")
        }
        if (!user.isEnabled) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account disabled")
        }
        val token = tokenProvider.generateToken(user.id, user.email)
        return AuthResponse(token, UserDto.from(user))
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: AppUserDetails?): UserDto {
        val user = principal?.user
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        return UserDto.from(user)
    }

    private fun provision(req: MagicLinkRequest): User {
        if (users.existsByPhone(req.phone)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Phone already registered to another account")
        }
        return users.save(
            User(
                email = req.email,
                phone = req.phone,
                zipcode = req.zipcode,
                access = AccessLevel.USER,
                isEnabled = true
            )
        )
    }
}
