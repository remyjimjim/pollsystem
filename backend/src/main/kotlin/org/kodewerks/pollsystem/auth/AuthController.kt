package org.kodewerks.pollsystem.auth

import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.UserRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.kodewerks.pollsystem.security.JwtTokenProvider
import jakarta.validation.Valid
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

// Local-profile test fixtures: emails matching this pattern get their access
// level inferred from the role token in the handle. Scoped to keep the rule
// from firing in prod even if an arbitrary user signs up with "testadmin" in
// their address.
private val TEST_FIXTURE_PATTERN = Regex(
    "^zzz\\d+test(user|viewer|creator|admin)@.+$",
    RegexOption.IGNORE_CASE
)

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val users: UserRepository,
    private val tokenProvider: JwtTokenProvider,
    private val magicLinks: MagicLinkService,
    private val emailer: MagicLinkEmailer,
    private val env: Environment
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
        val phone = req.phone ?: throw ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Phone is required to create an account"
        )
        val zipcode = req.zipcode ?: throw ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Zipcode is required to create an account"
        )
        if (users.existsByPhone(phone)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Phone already registered to another account")
        }
        return users.save(
            User(
                email = req.email,
                phone = phone,
                zipcode = zipcode,
                access = inferAccess(req.email),
                isEnabled = true
            )
        )
    }

    private fun inferAccess(email: String): AccessLevel {
        // Outside the local profile the fixture pattern is ignored — every new
        // user gets the default USER level regardless of email shape.
        if ("local" !in env.activeProfiles) return AccessLevel.USER
        val role = TEST_FIXTURE_PATTERN.matchEntire(email)?.groupValues?.get(1)?.lowercase()
        return when (role) {
            "admin"   -> AccessLevel.ADMIN
            "creator" -> AccessLevel.CREATOR
            "viewer"  -> AccessLevel.VIEWER
            "user"    -> AccessLevel.USER
            else      -> AccessLevel.USER
        }
    }
}
