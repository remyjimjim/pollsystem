package org.kodewerks.pollsystem.poll

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
                zipcode = zipcode,
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
                zipcode = zipcode,
                candidates = listOf(CandidateInput("Parent Candidate", "Ind", "Mayor")),
            ),
        )
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

        val ids = search(title = "public").map { it.id }
        assertThat(ids).contains(qId, eId)

        // Narrower term hits only the questionnaire.
        val artOnly = search(title = "art").map { it.id }
        assertThat(artOnly).contains(qId).doesNotContain(eId)

        // A term in neither title returns neither of ours.
        val none = search(title = "zzz-no-such-title").map { it.id }
        assertThat(none).doesNotContain(qId, eId)
    }

    @Test
    fun `type filter restricts results to one poll kind`() {
        val creator = creator()
        val qId = publishQuestionnaire(creator, "Type Q")
        val eId = publishElection(creator, "Type E")
        val bmId = publishBallotMeasure(creator, "Type BM")

        val elections = search(type = "Election")
        assertThat(elections.map { it.id }).contains(eId).doesNotContain(qId, bmId)
        assertThat(elections.filter { it.id == eId }.map { it.type }).containsExactly("Election")

        assertThat(search(type = "Questionnaire").map { it.id }).contains(qId).doesNotContain(eId, bmId)
        assertThat(search(type = "BallotMeasure").map { it.id }).contains(bmId).doesNotContain(qId, eId)
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
