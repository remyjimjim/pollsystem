package org.kodewerks.pollsystem.dev

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.kodewerks.pollsystem.authz.RoleAuthCache
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.BallotResponse
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.poll.BallotMeasureDraftRequest
import org.kodewerks.pollsystem.poll.BallotMeasureService
import org.kodewerks.pollsystem.poll.ElectionDraftRequest
import org.kodewerks.pollsystem.poll.ElectionService
import org.kodewerks.pollsystem.poll.QuestionInput
import org.kodewerks.pollsystem.poll.QuestionnaireDraftRequest
import org.kodewerks.pollsystem.poll.QuestionnaireService
import org.kodewerks.pollsystem.repository.BallotResponseRepository
import org.kodewerks.pollsystem.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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
    private val roleAuthCache: RoleAuthCache,
    private val questionnaires: QuestionnaireService,
    private val elections: ElectionService,
    private val ballotMeasures: BallotMeasureService,
    private val ballotResponses: BallotResponseRepository,
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

        // role_assignments deletion above moved authorization. Nuke the
        // cache so Playwright reruns see fresh empty state instead of a
        // stale "this user is authorized" entry from the previous suite.
        roleAuthCache.invalidateAuthorizations()

        log.info("Reset {} test users with email prefix '{}': ids={} deletions={}",
            matching.size, emailPrefix, ids, deletions)
        return mapOf(
            "deleted" to matching.size,
            "ids" to ids,
            "rowsByTable" to deletions
        )
    }

    /**
     * Seeds a single PUBLISHED questionnaire (with a `zzz`-prefixed creator, so
     * `reset-test-users` / global-teardown cleans it up) and returns its id and
     * unique title. Used by the search-and-complete e2e spec to have something
     * to find and respond to. The title carries a nanoTime suffix so each run's
     * poll is unique and a title search matches exactly it.
     */
    @PostMapping("/seed-questionnaire")
    @Transactional
    fun seedQuestionnaire(@RequestParam(defaultValue = "zzz") emailPrefix: String): Map<String, Any> {
        require(emailPrefix.length >= 3) {
            "emailPrefix must be at least 3 characters (safety guard)"
        }
        val n = System.nanoTime()
        val creator = users.save(
            User(
                email = "$emailPrefix-seedcreator-$n@test.local",
                phone = "+1555${(n % 10_000_000).toString().padStart(7, '0')}",
                zipcode = "90001",
                access = AccessLevel.CREATOR,
                isEnabled = true,
            )
        )
        val title = "E2E Search Poll $n"
        val draft = questionnaires.saveDraft(
            creator,
            QuestionnaireDraftRequest(
                pollTypeId = 2L,
                title = title,
                summary = "Seeded for the search-and-complete e2e test.",
                closeDate = null,
                questions = listOf(QuestionInput("Do you support automated testing?")),
                zipcodes = listOf("90001"),
            ),
        )
        questionnaires.publish(draft.id, creator, confirmed = false)
        log.info("Seeded published questionnaire id={} title='{}'", draft.id, title)
        return mapOf("id" to draft.id, "title" to title, "type" to "questionnaire")
    }

    /**
     * Seeds a single PUBLISHED ballot measure (with a `zzz`-prefixed creator, so
     * `reset-test-users` cleans it up) and returns its id, unique title, and zip.
     * A ballot measure hangs off an election (non-null FK), so this creates a
     * DRAFT election first (same creator, same zip — it need not be published for
     * the measure to appear in search). Used by the viewer-searches-views-results
     * e2e spec.
     */
    @PostMapping("/seed-ballot-measure")
    @Transactional
    fun seedBallotMeasure(
        @RequestParam(defaultValue = "zzz") emailPrefix: String,
        @RequestParam(defaultValue = "80202") zipcode: String,
    ): Map<String, Any> {
        require(emailPrefix.length >= 3) {
            "emailPrefix must be at least 3 characters (safety guard)"
        }
        require(zipcode.matches(Regex("^[0-9]{5}$"))) { "zipcode must be 5 digits" }
        val n = System.nanoTime()
        val creator = users.save(
            User(
                email = "$emailPrefix-bmcreator-$n@test.local",
                phone = "+1555${(n % 10_000_000).toString().padStart(7, '0')}",
                zipcode = zipcode,
                access = AccessLevel.CREATOR,
                isEnabled = true,
            )
        )
        val election = elections.saveDraft(
            creator,
            ElectionDraftRequest(
                pollTypeId = 1L, // Election
                title = "E2E Ballot Election $n",
                date = LocalDate.now(),
                zipcode = zipcode,
                closeDate = null,
                candidates = emptyList(),
            ),
        )
        val title = "E2E Ballot Measure $n"
        val draft = ballotMeasures.saveDraft(
            creator,
            BallotMeasureDraftRequest(
                pollTypeId = 3L, // Referendum / Ballot Measure
                electionId = election.id,
                title = title,
                summary = "Seeded for the viewer-searches-views-results e2e test.",
                effectiveDate = LocalDate.now(),
                closeDate = null,
            ),
        )
        ballotMeasures.publish(draft.id, creator, confirmed = false)
        log.info("Seeded published ballot measure id={} title='{}' zip={}", draft.id, title, zipcode)
        return mapOf("id" to draft.id, "title" to title, "zipcode" to zipcode, "electionId" to election.id)
    }

    /**
     * Seeds up to 6 ballot-measure responses from REAL registered users (created
     * here, `zzz`-prefixed, in the given zip so they count in the poll's purview).
     * Saved directly via the repository, which bypasses the paid-membership
     * participation guard — fine for seeding. Cap of 6 keeps totals below the
     * k-anonymity threshold (10) so a purview/geo-filtered results view withholds.
     */
    @PostMapping("/seed-ballot-responses")
    @Transactional
    fun seedBallotResponses(
        @RequestParam(defaultValue = "zzz") emailPrefix: String,
        @RequestParam measureId: Long,
        @RequestParam(defaultValue = "3") count: Int,
        @RequestParam(defaultValue = "80202") zipcode: String,
    ): Map<String, Any> {
        require(emailPrefix.length >= 3) {
            "emailPrefix must be at least 3 characters (safety guard)"
        }
        require(count in 1..6) {
            "count must be between 1 and 6 (stays below the k-anonymity threshold)"
        }
        require(zipcode.matches(Regex("^[0-9]{5}$"))) { "zipcode must be 5 digits" }
        val measure = ballotMeasures.get(measureId)
        val userIds = mutableListOf<Long>()
        repeat(count) { i ->
            val n = System.nanoTime()
            val voter = users.save(
                User(
                    email = "$emailPrefix-bmvoter-$n-$i@test.local",
                    phone = "+1555${((n + i) % 10_000_000).toString().padStart(7, '0')}",
                    zipcode = zipcode,
                    access = AccessLevel.USER,
                    isEnabled = true,
                )
            )
            ballotResponses.save(
                BallotResponse(measure = measure, user = voter, response = i % 2 == 0)
            )
            userIds.add(voter.id)
        }
        log.info("Seeded {} ballot responses for measure {} (zip {})", count, measureId, zipcode)
        return mapOf("seeded" to count, "measureId" to measureId, "userIds" to userIds)
    }

    /**
     * Seeds a single registered, active (paid) user via the API — no UI, no
     * Stripe. `zzz`-prefixed with a unique nanoTime email so re-runs don't
     * collide (cleaned by reset-test-users). Used by reuse specs (e.g.
     * user-submits-creator-request) that need an existing member to sign in as.
     */
    @PostMapping("/seed-user")
    @Transactional
    fun seedUser(
        @RequestParam(defaultValue = "zzz") emailPrefix: String,
        @RequestParam(defaultValue = "USER") access: String,
        @RequestParam(defaultValue = "80202") zipcode: String,
    ): Map<String, Any> {
        require(emailPrefix.length >= 3) {
            "emailPrefix must be at least 3 characters (safety guard)"
        }
        val n = System.nanoTime()
        val user = users.save(
            User(
                email = "$emailPrefix-user-$n@test.local",
                phone = "+1555${(n % 10_000_000).toString().padStart(7, '0')}",
                zipcode = zipcode,
                access = AccessLevel.valueOf(access.uppercase()),
                isEnabled = true,
                // Active member so member-gated flows (e.g. Become a Creator) work.
                paidUntil = Instant.now().plus(365, ChronoUnit.DAYS),
            )
        )
        log.info("Seeded {} user id={} email={}", user.access, user.id, user.email)
        return mapOf("id" to user.id, "email" to user.email)
    }

}
