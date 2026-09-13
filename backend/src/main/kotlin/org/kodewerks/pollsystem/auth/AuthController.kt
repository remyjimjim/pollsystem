package org.kodewerks.pollsystem.auth

import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.payment.PaymentProvider
import org.kodewerks.pollsystem.repository.CountyZipsRepository
import org.kodewerks.pollsystem.repository.UserRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.kodewerks.pollsystem.security.JwtTokenProvider
import jakarta.validation.Valid
import org.springframework.core.env.Environment
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
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
    private val countyZips: CountyZipsRepository,
    private val billing: PaymentProvider,
    private val env: Environment
) {

    /**
     * Request a magic-link sign-in for an *existing* account. In the pay-first
     * model accounts are created only by payment (Stripe checkout or the
     * Substack webhook), so an unknown email is **not** provisioned here —
     * it returns 404 and the caller routes the visitor to /register to pay.
     * (Under the `local` profile we still provision role-tagged fixtures so
     * Playwright e2e can seed users without going through Stripe.)
     */
    @PostMapping("/magic-link/request")
    fun requestMagicLink(@Valid @RequestBody req: MagicLinkRequest): ResponseEntity<Void> {
        val user = users.findByEmail(req.email.lowercase())
            ?: if ("local" in env.activeProfiles) provision(req)
               else throw ResponseStatusException(HttpStatus.NOT_FOUND, "No account for that email")
        val rawToken = magicLinks.issueToken(user)
        emailer.send(user, rawToken)
        return ResponseEntity.accepted().build()
    }

    /**
     * Tell the login screen whether an email is `UNKNOWN` (route to /register),
     * `LAPSED` (account exists but no active membership — send a link so they
     * can sign in and renew), or `ACTIVE` (send a link, straight in). This
     * deliberately reveals registration status — a product choice to route
     * visitors correctly, accepted as a trade-off against email enumeration.
     */
    @PostMapping("/status")
    fun accountStatus(@Valid @RequestBody req: AccountStatusRequest): AccountStatusResponse {
        val user = users.findByEmail(req.email.lowercase())
        val status = when {
            user == null || !user.isEnabled -> AccountStatus.UNKNOWN
            // CREATOR+ are exempt from the subscription gate (granted via other
            // flows); anyone else needs a live paid_until to count as active.
            user.hasActiveSubscription || user.access.ordinal >= AccessLevel.CREATOR.ordinal ->
                AccountStatus.ACTIVE
            else -> AccountStatus.LAPSED
        }
        return AccountStatusResponse(status)
    }

    /**
     * Pay-first registration entry point (public). Validates that the email and
     * phone are free and the zipcode is real, then returns a Stripe Checkout URL
     * that carries phone + zipcode in its metadata. The account itself is created
     * by the checkout webhook once payment succeeds — nothing is persisted here,
     * so a visitor who abandons checkout leaves no account behind.
     */
    @PostMapping("/register-checkout")
    fun registerCheckout(@Valid @RequestBody req: GuestCheckoutRequest): Map<String, String> {
        val email = req.email.lowercase()
        if (users.findByEmail(email) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "That email is already registered — sign in instead")
        }
        if (users.existsByPhone(req.phone)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "That phone is already registered to another account")
        }
        if (countyZips.findByZipcode(req.zipcode).isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown zipcode: ${req.zipcode}")
        }
        return mapOf("url" to billing.createGuestCheckoutSession(email, req.phone, req.zipcode))
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

    /**
     * Supply the phone + zipcode a payment-first user was provisioned without.
     * Required before they can participate (submit responses). Idempotent: a
     * user may re-submit their own phone; a phone held by another account 409s.
     */
    @PostMapping("/complete-profile")
    @Transactional
    fun completeProfile(
        @AuthenticationPrincipal principal: AppUserDetails?,
        @Valid @RequestBody req: CompleteProfileRequest
    ): UserDto {
        val current = principal?.user
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        if (countyZips.findByZipcode(req.zipcode).isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown zipcode: ${req.zipcode}")
        }
        val holder = users.findByPhone(req.phone)
        if (holder != null && holder.id != current.id) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Phone already registered to another account")
        }
        val updated = users.save(current.copy(phone = req.phone, zipcode = req.zipcode))
        return UserDto.from(updated)
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
