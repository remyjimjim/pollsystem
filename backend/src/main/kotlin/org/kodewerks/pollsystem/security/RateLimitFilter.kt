package org.kodewerks.pollsystem.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/** Per-IP limits on the public endpoints (see [RateLimiter] for the numbers). */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class RateLimitFilter(
    private val limiter: RateLimiter,
    private val ips: ClientIpResolver
) : OncePerRequestFilter() {

    private fun ruleFor(request: HttpServletRequest): RateLimiter.Rule? {
        val path = request.requestURI
        return when {
            request.method == "POST" && path == "/api/auth/magic-link/request" -> RateLimiter.MAGIC_LINK_IP
            request.method == "POST" && path == "/api/auth/status" -> RateLimiter.STATUS_IP
            request.method == "POST" && path == "/api/auth/register-checkout" -> RateLimiter.CHECKOUT_IP
            request.method == "GET" && (path == "/api/polls/search" || path.startsWith("/api/polls/search/")) -> RateLimiter.SEARCH_IP
            else -> null
        }
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !limiter.enabled || ruleFor(request) == null

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val rule = ruleFor(request)
        val ip = ips.resolve(request)
        val retryAfter = if (rule != null && ip != null) limiter.tryAcquire(rule, ip) else null
        if (retryAfter != null) {
            response.status = 429
            response.setHeader(HttpHeaders.RETRY_AFTER, retryAfter.toString())
            response.contentType = "application/json"
            response.writer.write("""{"status":429,"error":"Too Many Requests","message":"Too many requests. Please wait a few minutes and try again."}""")
            return
        }
        chain.doFilter(request, response)
    }
}
