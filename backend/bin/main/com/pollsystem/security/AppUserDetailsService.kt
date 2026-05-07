package com.pollsystem.security

import com.pollsystem.repository.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class AppUserDetailsService(private val users: UserRepository) : UserDetailsService {
    override fun loadUserByUsername(email: String): UserDetails {
        val user = users.findByEmail(email)
            ?: throw UsernameNotFoundException("User not found: $email")
        return AppUserDetails(user)
    }

    fun loadById(id: Long): UserDetails? =
        users.findById(id).orElse(null)?.let { AppUserDetails(it) }
}
