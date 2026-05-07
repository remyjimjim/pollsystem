package com.pollsystem.auth

import com.pollsystem.model.AccessLevel
import com.pollsystem.model.User
import com.pollsystem.repository.UserRepository
import com.pollsystem.security.AppUserDetails
import com.pollsystem.security.JwtTokenProvider
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.crypto.password.PasswordEncoder
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
    private val passwordEncoder: PasswordEncoder,
    private val authManager: AuthenticationManager,
    private val tokenProvider: JwtTokenProvider
) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody req: RegisterRequest): ResponseEntity<AuthResponse> {
        if (users.existsByEmail(req.email)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Email already registered")
        }
        if (users.existsByPhone(req.phone)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Phone already registered")
        }
        val saved = users.save(
            User(
                email = req.email,
                phone = req.phone,
                zipcode = req.zipcode,
                passcode = passwordEncoder.encode(req.passcode),
                access = AccessLevel.USER,
                isEnabled = true
            )
        )
        val token = tokenProvider.generateToken(saved.id, saved.email)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(AuthResponse(token, UserDto.from(saved)))
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody req: LoginRequest): AuthResponse {
        try {
            authManager.authenticate(
                UsernamePasswordAuthenticationToken(req.email, req.passcode)
            )
        } catch (_: BadCredentialsException) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password")
        }
        val user = users.findByEmail(req.email)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password")
        val token = tokenProvider.generateToken(user.id, user.email)
        return AuthResponse(token, UserDto.from(user))
    }

    @GetMapping("/me")
    fun me(@AuthenticationPrincipal principal: AppUserDetails?): UserDto {
        val user = principal?.user
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        return UserDto.from(user)
    }
}
