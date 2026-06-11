package org.kodewerks.pollsystem.security

import org.kodewerks.pollsystem.repository.UserRepository
import org.springframework.stereotype.Service

/**
 * Resolves authenticated principals by id (after JWT verification) or email
 * (for any code path that still wants UserDetailsService semantics). This
 * service is no longer used for password-based authentication — the
 * DaoAuthenticationProvider was removed when auth moved to magic-link only.
 */
@Service
class AppUserDetailsService(private val users: UserRepository) {
    fun loadByEmail(email: String): AppUserDetails? =
        users.findByEmail(email)?.let { AppUserDetails(it) }

    fun loadById(id: Long): AppUserDetails? =
        users.findById(id).orElse(null)?.let { AppUserDetails(it) }
}
