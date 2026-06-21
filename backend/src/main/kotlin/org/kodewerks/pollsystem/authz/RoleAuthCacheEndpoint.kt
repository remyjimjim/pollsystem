package org.kodewerks.pollsystem.authz

import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation
import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import org.springframework.stereotype.Component

/**
 * Actuator endpoint at `/actuator/rolecache` exposing the
 * [RoleAuthCache] snapshot (size, hit rate, eviction count, …) for
 * both the authorizations and pending-counts caches.
 *
 * Read-only GET to inspect stats; DELETE to manually nuke the cache —
 * useful when comparing cold-start latency vs. warmed behaviour during
 * profiling, or when debugging a suspected stale-authorization issue
 * before chasing the actual write site that forgot to invalidate.
 *
 * Endpoint visibility is gated two ways: it must be listed in
 * `management.endpoints.web.exposure.include` (see application.yml),
 * and SecurityConfig restricts the actuator path tree to the SUPER
 * role. Health stays publicly accessible at `/actuator/health`
 * regardless. (Note: Kotlin nests block comments, so writing the
 * actuator glob with double-star inline would re-open this KDoc as a
 * nested comment and produce a parser error.)
 */
@Component
@Endpoint(id = "rolecache")
class RoleAuthCacheEndpoint(private val cache: RoleAuthCache) {

    @ReadOperation
    fun read(): RoleAuthCache.Snapshot = cache.snapshot()

    @DeleteOperation
    fun reset(): Map<String, Any> {
        val before = cache.snapshot()
        cache.invalidateAuthorizations()
        return mapOf(
            "cleared" to true,
            "authorizationsSizeBefore" to before.authorizations.size,
        )
    }
}
