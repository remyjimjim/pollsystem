package org.kodewerks.pollsystem.security

import jakarta.servlet.DispatcherType
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.cors.CorsConfigurationSource

@Configuration
class SecurityConfig(
    private val jwtFilter: JwtAuthenticationFilter
) {

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOrigins = listOf("http://localhost:3000")
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { }
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                // Stateless JWT API: missing/invalid auth → 401 (not the Spring 6
                // default of 403). 403 still applies when an authenticated user
                // lacks the required role.
                it.authenticationEntryPoint(HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
            }
            .authorizeHttpRequests {
                // Spring Security 6 reapplies the filter chain to internal
                // ERROR/FORWARD dispatches, which overwrites the controller's
                // real status (e.g. 409) with a 401. Permit those dispatch
                // types so error responses surface unchanged.
                it.dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                it.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                it.requestMatchers(
                    "/api/auth/magic-link/**",
                    "/webhooks/stripe",
                    "/api/states/**",
                    "/api/counties/**",
                    "/api/zipcodes/**",
                    "/api/poll-types/**",
                    "/api/polls/search",
                    "/api/polls/search/**",
                    "/api/polls/*/*/results/**",
                    // DevController is @Profile("local"), so under prod the
                    // path 404s and permitAll has no effect there.
                    "/api/dev/**",
                    // Liveness/readiness probe — orchestrators need
                    // unauthenticated access. Other actuator endpoints
                    // require SUPER below.
                    "/actuator/health",
                ).permitAll()
                it.requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "SUPER")
                it.requestMatchers("/api/super/**").hasRole("SUPER")
                it.requestMatchers("/actuator/**").hasRole("SUPER")
                it.anyRequest().authenticated()
            }
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
