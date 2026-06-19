package org.kodewerks.pollsystem.dev

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.kodewerks.pollsystem.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// Dev-only utilities. @Profile("local") means the bean is not registered
// under the `test` or `prod` profiles — in prod the path 404s because no
// handler exists, regardless of what SecurityConfig allows.
//
// Typical use: Playwright e2e tests call POST /api/dev/reset-test-users
// from a beforeAll hook to clear leftover users (UNIQUE on email + phone)
// from the previous run, so deterministic fixtures don't trip the
// uniqueness constraints on re-registration.
@RestController
@RequestMapping("/api/dev")
@Profile("local")
class DevController(
    private val users: UserRepository,
) {

    @PersistenceContext
    private lateinit var em: EntityManager

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/reset-test-users")
    @Transactional
    fun resetTestUsers(@RequestParam emailPrefix: String): Map<String, Any> {
        // Refuse short prefixes: stops a typo like "" or "z" from nuking
        // every user in the dev DB.
        require(emailPrefix.length >= 3) {
            "emailPrefix must be at least 3 characters (safety guard)"
        }

        val matching = users.findByEmailStartingWithIgnoreCaseOrderByEmail(emailPrefix)
        if (matching.isEmpty()) return mapOf("deleted" to 0, "ids" to emptyList<Long>())

        val ids = matching.map { it.id }
        // Safe to interpolate: ids come from JpaRepository, never user input.
        val idList = ids.joinToString(",")
        val deletions = linkedMapOf<String, Int>()

        fun nuke(table: String, where: String) {
            deletions[table] = em.createNativeQuery("DELETE FROM $table WHERE $where").executeUpdate()
        }

        // Order matters — children first. Covers every FK to users(id) in
        // the V1..V14 schema. Most counts will be zero for users that only
        // registered via magic link; the wide net handles stale state from
        // manual testing.

        // Polls the user authored: cascade child responses, candidates,
        // questions, domains, then the polls themselves.
        em.createNativeQuery("""
            DELETE FROM candidate_responses WHERE candidate_id IN (
                SELECT c.id FROM candidates c
                WHERE c.election_id IN (SELECT id FROM elections WHERE creator_id IN ($idList))
            )
        """).executeUpdate()
        em.createNativeQuery("""
            DELETE FROM candidates WHERE election_id IN (
                SELECT id FROM elections WHERE creator_id IN ($idList)
            )
        """).executeUpdate()
        nuke("elections", "creator_id IN ($idList)")

        em.createNativeQuery("""
            DELETE FROM ballot_responses WHERE measure_id IN (
                SELECT id FROM ballot_measures WHERE creator_id IN ($idList)
            )
        """).executeUpdate()
        nuke("ballot_measures", "creator_id IN ($idList)")

        em.createNativeQuery("""
            DELETE FROM question_responses WHERE question_id IN (
                SELECT q.id FROM questions q
                WHERE q.questionnaire_id IN (SELECT id FROM questionnaires WHERE creator_id IN ($idList))
            )
        """).executeUpdate()
        em.createNativeQuery("""
            DELETE FROM questions WHERE questionnaire_id IN (
                SELECT id FROM questionnaires WHERE creator_id IN ($idList)
            )
        """).executeUpdate()
        em.createNativeQuery("""
            DELETE FROM questionnaire_domains WHERE questionnaire_id IN (
                SELECT id FROM questionnaires WHERE creator_id IN ($idList)
            )
        """).executeUpdate()
        nuke("questionnaires", "creator_id IN ($idList)")

        // Direct references from auxiliary tables. user_messages.user_id is
        // ON DELETE CASCADE (V13); author_id is not, so clear it explicitly.
        nuke("magic_link_tokens", "user_id IN ($idList)")
        nuke("role_assignments", "user_id IN ($idList)")
        nuke("candidate_responses", "user_id IN ($idList)")
        nuke("ballot_responses", "user_id IN ($idList)")
        nuke("question_responses", "user_id IN ($idList)")
        nuke("user_messages", "author_id IN ($idList)")
        nuke("creator_requests", "user_id IN ($idList) OR assigned_admin_id IN ($idList) OR processed_by_id IN ($idList)")
        nuke("admin_requests", "user_id IN ($idList) OR processed_by_id IN ($idList)")
        nuke("ip_rules", "created_by_id IN ($idList)")
        nuke("poll_type_blocks", "created_by IN ($idList)")
        nuke("poll_notes", "author_id IN ($idList)")

        nuke("users", "id IN ($idList)")

        log.info("Reset {} test users with email prefix '{}': ids={} deletions={}",
            matching.size, emailPrefix, ids, deletions)
        return mapOf(
            "deleted" to matching.size,
            "ids" to ids,
            "rowsByTable" to deletions
        )
    }

}
