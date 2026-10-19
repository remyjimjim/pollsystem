package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.QuestionnaireRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Coverage for PollSearchController — the public `/api/polls/search` endpoint,
 * which previously had no test at any level. Exercises the title / type /
 * candidate / geo filters, the published-and-active gate, the includeClosed
 * toggle, and the suggestions datalist feed.
 *
 * Polls are built through the real services (saveDraft + publish), so each poll
 * kind's V1-seeded pollType (Election=1, Questionnaire=2, BallotMeasure=3) and
 * its active/expired semantics are exercised as the controller sees them.
 * Assertions match on the ids/titles this test creates (contains / doesNotContain)
 * so unrelated rows can never make them flaky.
 */
class PollSearchControllerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var controller: PollSearchController
    @Autowired private lateinit var questionnaireService: QuestionnaireService
    @Autowired private lateinit var electionService: ElectionService
    @Autowired private lateinit var ballotMeasureService: BallotMeasureService
    @Autowired private lateinit var questionnaireRepo: QuestionnaireRepository
    @Autowired private lateinit var fixtures: TestFixtures

    private fun creator() = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")

    /** Thin wrapper so tests only name the params they care about. */
    private fun search(
        title: String? = null,
        zipcodes: List<String>? = null,
        creatorEmail: String? = null,
        candidateName: String? = null,
        type: String? = null,
        includeClosed: Boolean = false,
        viewer: User? = null,
    ): List<PollSearchResult> =
        controller.search(
            title = title,
            zipcodes = zipcodes,
            countyIds = null,
            stateIds = null,
            creatorEmail = creatorEmail,
            candidateName = candidateName,
            type = type,
            includeClosed = includeClosed,
            principal = viewer?.let { org.kodewerks.pollsystem.security.AppUserDetails(it) },
        )

    private fun publishQuestionnaire(
        creator: User,
        title: String,
        zipcodes: List<String> = listOf("90001"),
        closeDate: Instant? = null,
    ): Long {
        val draft = questionnaireService.saveDraft(
            creator,
            QuestionnaireDraftRequest(
                pollTypeId = 2L,
                title = title,
                summary = "summary",
                closeDate = closeDate,
                questions = listOf(QuestionInput("Should we?")),
                zipcodes = zipcodes,
            ),
        )
        questionnaireService.publish(draft.id, creator, confirmed = false)
        return draft.id
    }

    private fun publishElection(
        creator: User,
        title: String,
        zipcode: String = "90001",
        candidates: List<CandidateInput> = listOf(CandidateInput("Default Candidate", "Ind", "Mayor")),
    ): Long {
        val draft = electionService.saveDraft(
            creator,
            ElectionDraftRequest(
                pollTypeId = 1L,
                title = title,
                date = LocalDate.now().plusDays(30),
                scopeLevel = ScopeLevel.NATIONAL,
                candidates = candidates,
            ),
        )
        electionService.publish(draft.id, creator, confirmed = false)
        return draft.id
    }

    private fun publishBallotMeasure(creator: User, title: String, zipcode: String = "90001"): Long {
        val parent = electionService.saveDraft(
            creator,
            ElectionDraftRequest(
                pollTypeId = 1L,
                title = "$title (parent)",
                date = LocalDate.now().plusDays(30),
                scopeLevel = ScopeLevel.NATIONAL,
                candidates = listOf(CandidateInput("Parent Candidate", "Ind", "Mayor")),
            ),
        )
        // A measure is published on its own only under a published election (4a/5a).
        electionService.publish(parent.id, creator, confirmed = true)
        val draft = ballotMeasureService.saveDraft(
            creator,
            BallotMeasureDraftRequest(
                pollTypeId = 3L,
                electionId = parent.id,
                title = title,
                summary = "Approve?",
                effectiveDate = LocalDate.now().plusDays(30),
                closeDate = Instant.now().plus(30, ChronoUnit.DAYS),
            ),
        )
        ballotMeasureService.publish(draft.id, creator, confirmed = false)
        return draft.id
    }

    @Test
    fun `title filter matches case-insensitively across poll kinds`() {
        val creator = creator()
        val qId = publishQuestionnaire(creator, "Public Art Funding")
        val eId = publishElection(creator, "Public Transit Board")

        // Ids are per poll type (separate tables), so compare type + id.
        val q = "Questionnaire:$qId"
        val e = "Election:$eId"
        fun found(title: String) = search(title = title).map { "${it.type}:${it.id}" }
        assertThat(found("public")).contains(q, e)

        // Narrower term hits only the questionnaire.
        assertThat(found("art")).contains(q).doesNotContain(e)

        // A term in neither title returns neither of ours.
        assertThat(found("zzz-no-such-title")).doesNotContain(q, e)
    }

    @Test
    fun `type filter restricts results to one poll kind`() {
        val creator = creator()
        val qId = publishQuestionnaire(creator, "Type Q")
        val eId = publishElection(creator, "Type E")
        val bmId = publishBallotMeasure(creator, "Type BM")

        // Ids are per-kind sequences and can coincide across kinds, so compare (type, id).
        val q = "Questionnaire" to qId
        val e = "Election" to eId
        val bm = "BallotMeasure" to bmId
        fun keys(type: String) = search(type = type).map { it.type to it.id }

        assertThat(keys("Election")).contains(e).doesNotContain(q, bm)
        assertThat(keys("Questionnaire")).contains(q).doesNotContain(e, bm)
        assertThat(keys("BallotMeasure")).contains(bm).doesNotContain(q, e)
    }

    @Test
    fun `candidate name filter matches an election by its roster`() {
        val creator = creator()
        val eId = publishElection(
            creator,
            "School Board",
            candidates = listOf(CandidateInput("Alice Johnson", "Ind", "Director")),
        )
        val qId = publishQuestionnaire(creator, "Unrelated Survey")

        val ids = search(candidateName = "johnson").map { it.id }
        assertThat(ids).contains(eId)
        // A questionnaire has no candidates, so the candidate filter excludes it.
        assertThat(ids).doesNotContain(qId)
    }

    @Test
    fun `zipcode filter excludes polls outside the zip`() {
        val creator = creator()
        val id = publishQuestionnaire(creator, "Geo Scoped", zipcodes = listOf("90001"))

        assertThat(search(zipcodes = listOf("90001")).map { it.id }).contains(id)
        assertThat(search(zipcodes = listOf("90012")).map { it.id }).doesNotContain(id)
    }

    @Test
    fun `draft polls are not returned`() {
        val creator = creator()
        // saveDraft WITHOUT publish — must not surface in search (active = PUBLISHED).
        val draft = questionnaireService.saveDraft(
            creator,
            QuestionnaireDraftRequest(
                pollTypeId = 2L,
                title = "Still A Draft",
                summary = "summary",
                closeDate = null,
                questions = listOf(QuestionInput("Should we?")),
                zipcodes = listOf("90001"),
            ),
        )
        assertThat(search().map { it.id }).doesNotContain(draft.id)
        assertThat(search(title = "still a draft").map { it.id }).doesNotContain(draft.id)
    }

    @Test
    fun `closed polls appear only when includeClosed is true`() {
        val creator = creator()
        // Publish with a valid future close date (first-publish forbids a past
        // one), then wind the clock forward by writing a past close date — the
        // supported way to simulate a poll that has since closed.
        val id = publishQuestionnaire(
            creator,
            "Recently Closed",
            closeDate = Instant.now().plus(30, ChronoUnit.DAYS),
        )
        val q = questionnaireRepo.findById(id).orElseThrow()
        questionnaireRepo.save(q.copy(closeDate = Instant.now().minus(1, ChronoUnit.DAYS)))

        assertThat(search().map { it.id }).doesNotContain(id)
        assertThat(search(includeClosed = true).map { it.id }).contains(id)
    }

    @Test
    fun `creator emails go to admins and supers only, and only they can filter by one`() {
        val c = creator()
        val qId = publishQuestionnaire(c, "Creator column ${System.nanoTime()}")
        val e = publishElection(c, "Creator column E ${System.nanoTime()}")
        val m = publishBallotMeasure(c, "Creator column M ${System.nanoTime()}")
        // Ids are per poll type (separate tables), so match type + id.
        val mine = setOf("Questionnaire" to qId, "Election" to e, "BallotMeasure" to m)
        fun rows(viewer: User?, byEmail: String? = null) =
            search(creatorEmail = byEmail, viewer = viewer).filter { (it.type to it.id) in mine }

        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "sc-admin")
        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "sc-super")
        for (staff in listOf(admin, sup)) {
            assertThat(rows(staff).map { it.creatorEmail }).hasSize(3).containsOnly(c.email)
            assertThat(rows(staff, byEmail = "nobody-${System.nanoTime()}")).isEmpty()
        }

        val member = fixtures.createUser(emailPrefix = "sc-user")
        for (viewer in listOf(null, member, c)) {
            assertThat(rows(viewer).map { it.creatorEmail }).hasSize(3).containsOnlyNulls()
            // The filter is ignored, so it can't be used to test who created what.
            assertThat(rows(viewer, byEmail = "nobody-${System.nanoTime()}")).hasSize(3)
        }
    }

    @Autowired private lateinit var responseController: QuestionnaireResponseController
    @Autowired private lateinit var questionRepo: org.kodewerks.pollsystem.repository.QuestionRepository

    @Test
    fun `each result says how many people have responded`() {
        val id = publishQuestionnaire(creator(), "Responses column ${System.nanoTime()}")
        val q = questionRepo.findByQuestionnaireId(id).single()
        repeat(2) {
            responseController.submit(
                org.kodewerks.pollsystem.security.AppUserDetails(fixtures.createUser(emailPrefix = "resp")), id,
                SubmitResponsesRequest(listOf(QuestionAnswerInput(q.id, "Yes"))))
        }
        assertThat(search().single { it.id == id && it.type == "Questionnaire" }.respondents).isEqualTo(2)
    }

    @Test
    fun `suggestions returns active titles and candidate names`() {
        val creator = creator()
        publishQuestionnaire(creator, "Public Art Funding")
        publishElection(
            creator,
            "City Council Race",
            candidates = listOf(CandidateInput("Alice Johnson", "Ind", "Mayor")),
        )

        val s = controller.suggestions()
        assertThat(s.titles).contains("Public Art Funding", "City Council Race")
        assertThat(s.candidates).contains("Alice Johnson")
    }
}
