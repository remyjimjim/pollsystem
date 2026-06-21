package org.kodewerks.pollsystem.authz

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.RequestStatus
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.CreatorRequestRepository
import org.kodewerks.pollsystem.repository.RoleAssignmentRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * In-process cache layer in front of the hot role_assignments reads.
 *
 * Why this exists: role_assignments stores authorization at zipcode
 * granularity, so a single creator with nation-scope access materializes
 * as ~33,000 rows. Reads like `findEnabledByRoleAndZipcodes` traverse the
 * (role, zipcode) index per zip in the input list. Caching the
 * (role, pollType, zipcode) → user-id-set tuple turns those reads into
 * O(1) map probes.
 *
 * Two cohabiting caches:
 *  - `usersByAuthKey` carries the heavy lookup (used by
 *    `CreatorRequestService.routeToAdmin` and admin-dashboard "in my
 *    purview" reads). Invalidated en bloc on every role_assignments
 *    write — see `invalidateAuthorizations()`. That's coarse but cheap:
 *    approvals happen at human-click frequency, the cache cold-starts in
 *    a few hundred reads, and we avoid maintaining a zipcode→key
 *    reverse index for surgical eviction.
 *  - `pendingCountByAdmin` covers the per-admin "PENDING creator
 *    requests assigned to me" counter that `routeToAdmin` hits inside
 *    its `minBy { … }` tiebreak. Small TTL because correctness only
 *    affects load-balancing fairness — a slightly stale count picks an
 *    admin who's already at +1 pending, not a security violation.
 *
 * Storage budget: 33k zipcodes × 5 AccessLevel × ~50 user-ids each ≈
 * a few MB in the worst case. Caffeine bounds it at maximumSize anyway.
 */
@Component
class RoleAuthCache(
    private val roleAssignments: RoleAssignmentRepository,
    private val creatorRequests: CreatorRequestRepository,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    data class AuthKey(
        val role: AccessLevel,
        val pollTypeId: Long?,
        val zipcode: String,
    )

    private val usersByAuthKey: Cache<AuthKey, Set<Long>> =
        Caffeine.newBuilder()
            .maximumSize(100_000)
            .recordStats()
            .build()

    private val pendingCountByAdmin: Cache<Long, Long> =
        Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .recordStats()
            .build()

    /**
     * Distinct enabled-user ids authorized for [role] in any of [zipcodes].
     * `pollTypeId = null` means "any poll type" — matches the existing
     * `findEnabledByRoleAndZipcodes` behaviour which doesn't filter by
     * poll type.
     */
    fun usersAuthorized(
        role: AccessLevel,
        zipcodes: List<String>,
        pollTypeId: Long? = null,
    ): Set<Long> {
        if (zipcodes.isEmpty()) return emptySet()
        val out = LinkedHashSet<Long>()
        for (zip in zipcodes.distinct()) {
            val key = AuthKey(role, pollTypeId, zip)
            out += usersByAuthKey.get(key) { loadForKey(it) }
        }
        return out
    }

    /**
     * PENDING creator-request count assigned to [admin]. Used as the
     * tiebreak when several admins could route a new request.
     */
    fun pendingCount(admin: User): Long =
        pendingCountByAdmin.get(admin.id) {
            creatorRequests.countByAssignedAdminAndStatus(admin, RequestStatus.PENDING)
        }

    /**
     * Nuke the authorization cache. Called from every write site that
     * mutates role_assignments — approval flips enabled true/false,
     * creator-request submission inserts new rows, admin-request
     * submission inserts new rows, etc. Coarse but cheap.
     */
    fun invalidateAuthorizations() {
        val before = usersByAuthKey.estimatedSize()
        usersByAuthKey.invalidateAll()
        log.debug("RoleAuthCache.invalidateAuthorizations: cleared ~{} entries", before)
    }

    /**
     * Bump the pending-count cache for [admin] without re-querying. Used
     * after we successfully assign a fresh creator request — the count
     * we just looked up is now one too low. Cheaper than invalidating
     * and re-fetching on the next routing call.
     */
    fun bumpPendingCount(admin: User, delta: Long = 1L) {
        val current = pendingCountByAdmin.getIfPresent(admin.id)
        if (current != null) pendingCountByAdmin.put(admin.id, (current + delta).coerceAtLeast(0))
    }

    private fun loadForKey(key: AuthKey): Set<Long> {
        // Single-zipcode lookup: passes a list of one through to keep the
        // repo method shape unchanged.
        return roleAssignments
            .findEnabledByRoleAndZipcodes(key.role, listOf(key.zipcode))
            .asSequence()
            .filter { key.pollTypeId == null || it.pollType?.id == key.pollTypeId }
            .map { it.user }
            .filter { it.isEnabled }
            .map { it.id }
            .toSet()
    }
}
