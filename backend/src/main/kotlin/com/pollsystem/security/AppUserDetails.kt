package com.pollsystem.security

import com.pollsystem.model.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

/**
 * Auth is magic-link only — there is no password to return. UserDetails still
 * exists because the JWT filter populates the SecurityContext with one, and
 * Spring Security's authorization layer reads roles from it.
 */
class AppUserDetails(val user: User) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> =
        listOf(SimpleGrantedAuthority("ROLE_${user.access.name}"))

    override fun getPassword(): String = ""
    override fun getUsername(): String = user.email
    override fun isAccountNonExpired(): Boolean = true
    override fun isAccountNonLocked(): Boolean = true
    override fun isCredentialsNonExpired(): Boolean = true
    override fun isEnabled(): Boolean = user.isEnabled
}
