package org.kodewerks.pollsystem.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * The visitor's IP, for per-IP rate limits.
 *
 * On staging and prod, browsers reach the backend through the Cloudflare Pages
 * proxy (frontend/functions/api/[[path]].js), so the connection comes from
 * Cloudflare, not the visitor. The proxy passes the visitor's IP in
 * X-Client-IP, together with a shared secret in X-Proxy-Secret
 * (app.proxy.secret, PROXY_SECRET on both sides), so it can't be faked by
 * calling the backend directly.
 *
 * Returns null while no secret is configured: per-IP limits stay off rather
 * than treating every proxied visitor as one. Requests without the right
 * secret (direct calls to the backend) are keyed by their own connection.
 */
@Component
class ClientIpResolver(@Value("\${app.proxy.secret:}") private val secret: String) {

    val configured: Boolean get() = secret.isNotBlank()

    fun resolve(request: HttpServletRequest): String? {
        if (!configured) return null
        val sent = request.getHeader("X-Proxy-Secret")
        if (sent != null && MessageDigest.isEqual(sent.toByteArray(), secret.toByteArray())) {
            request.getHeader("X-Client-IP")?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        // Not via the proxy: the connection's own address (Fly puts it in Fly-Client-IP).
        return request.getHeader("Fly-Client-IP")?.trim()?.takeIf { it.isNotEmpty() } ?: request.remoteAddr
    }
}
