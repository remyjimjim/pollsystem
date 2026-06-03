package com.pollsystem.superadmin

import com.pollsystem.model.AccessLevel
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Component

/**
 * Bulk-rewrites every RoleAssignment owned by a user to a new role.
 *
 * We can't ride the JPA dirty-update path: the entity uses Kotlin `val`
 * (immutable) and the `role` column is mapped through
 * `@JdbcTypeCode(NAMED_ENUM)` to a Postgres ENUM type. A
 * `findByUserId + saveAll(copy(role = …))` either no-ops (merge sees
 * the data-class copy as a "new" detached instance with the same id
 * and silently picks the cached managed one) or, with @Modifying JPQL,
 * Hibernate's parameter binding doesn't match the ENUM column.
 *
 * Plain JDBC with an explicit `CAST` writes the row reliably; we then
 * flush + clear the persistence context so subsequent reads in the
 * same transaction observe the new value instead of the stale cached
 * entity.
 */
@Component
class RoleAssignmentBulkOps(
    @PersistenceContext private val em: EntityManager
) {
    fun updateRoleForUser(userId: Long, newRole: AccessLevel): Int {
        em.flush()
        val updated = em.createNativeQuery(
            "UPDATE role_assignments SET role = CAST(:role AS access_level) WHERE user_id = :userId"
        )
            .setParameter("role", newRole.name)
            .setParameter("userId", userId)
            .executeUpdate()
        em.clear()
        return updated
    }
}
