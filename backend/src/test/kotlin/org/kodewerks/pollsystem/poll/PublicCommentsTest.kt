package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.moderation.BadWords
import org.kodewerks.pollsystem.repository.CandidateRepository
import org.kodewerks.pollsystem.repository.QuestionRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

/**
 * Public comments (decided 2026-10-15: whole-word matching, a declined
 * replacement keeps the comment private, shown anonymously on results with the
 * same small-poll privacy). Test k = 3. Uses the real moderation/bad-words.json,
 * without naming its words here.
 */
class PublicCommentsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var badWords: BadWords
    @Autowired private lateinit var service: QuestionnaireService
    @Autowired private lateinit var questions: QuestionRepository
    @Autowired private lateinit var responses: QuestionnaireResponseController
    @Autowired private lateinit var results: QuestionnaireResultsController
    @Autowired private lateinit var elections: ElectionService
    @Autowired private lateinit var candidates: CandidateRepository
    @Autowired private lateinit var electionResponses: ElectionResponseController
    @Autowired private lateinit var electionResults: ElectionResultsController
    @Autowired private lateinit var measures: BallotMeasureService
    @Autowired private lateinit var measureResponses: BallotMeasureResponseController
    @Autowired private lateinit var measureResults: BallotMeasureResultsController
    @Autowired private lateinit var fixtures: TestFixtures

    private val bad get() = badWords.list.first().word

    private fun poll(): Long {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "pc-creator")
        val d = service.saveDraft(creator, QuestionnaireDraftRequest(
            pollTypeId = 2L, title = "Comments", summary = "s", closeDate = null,
            questions = listOf(QuestionInput("Q?")), zipcodes = listOf("90001")
        ))
        service.publish(d.id, creator, confirmed = false)
        return d.id
    }

    private fun answer(pollId: Long, comment: String?, public: Boolean, voter: User = fixtures.createUser(emailPrefix = "pc-v")) =
        responses.submit(AppUserDetails(voter), pollId, SubmitResponsesRequest(listOf(
            QuestionAnswerInput(questions.findByQuestionnaireId(pollId).single().id, "Yes", comment, public)
        ))).responses.single()

    private fun shown(pollId: Long) = results.get(pollId).perQuestion.singleOrNull()?.comments.orEmpty()

    @Test
    fun `the list loads, and matching is whole-word and ignores case`() {
        assertThat(badWords.list).isNotEmpty
        assertThat(badWords.foundIn("Well, ${bad.uppercase()}!")).extracting<String> { it.word }.contains(bad)
        assertThat(badWords.foundIn("${bad}zz zz$bad")).isEmpty()
        assertThat(badWords.foundIn("All good here")).isEmpty()
    }

    @Test
    fun `a clean comment can be public, one with a listed word stays private`() {
        val id = poll()
        assertThat(answer(id, "Fine idea", public = true).commentPublic).isTrue()
        assertThat(answer(id, "What a $bad idea", public = true).commentPublic).isFalse()
        assertThat(answer(id, "Fine idea", public = false).commentPublic).isFalse()
        assertThat(answer(id, "   ", public = true).commentPublic).isFalse()
    }

    @Test
    fun `public comments show anonymously, in the same privacy steps as the results`() {
        val id = poll()
        answer(id, "First public", public = true)
        answer(id, "Kept private", public = false)
        assertThat(shown(id)).isEmpty()                       // under k: no results, no comments

        answer(id, "Third public", public = true)             // k reached: the first snapshot
        assertThat(shown(id)).containsExactly("First public", "Third public")

        answer(id, "Fourth public", public = true)            // not in the snapshot yet
        assertThat(shown(id)).containsExactly("First public", "Third public")

        // A super sees the live view, still public comments only.
        val sup = AppUserDetails(fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "pc-super"))
        assertThat(results.get(id, principal = sup).perQuestion.single().comments)
            .containsExactly("First public", "Third public", "Fourth public")
    }

    @Test
    fun `making a comment private again hides it`() {
        val id = poll()
        val author = fixtures.createUser(emailPrefix = "pc-author")
        answer(id, "Changed my mind", public = true, voter = author)
        repeat(2) { answer(id, null, public = false) }
        assertThat(shown(id)).containsExactly("Changed my mind")
        answer(id, "Changed my mind", public = false, voter = author)
        assertThat(shown(id)).isEmpty()
    }

    @Test
    fun `elections and ballot measures show public comments too`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "pc-creator")
        val e = elections.saveDraft(creator, ElectionDraftRequest(
            pollTypeId = 1L, title = "E", date = LocalDate.now().plusDays(30), scopeLevel = ScopeLevel.NATIONAL,
            candidates = listOf(CandidateInput("A", "X", "Mayor"))
        ))
        elections.publish(e.id, creator, confirmed = false)
        val cand = candidates.findByElectionId(e.id).single().id
        val m = measures.saveDraft(creator, BallotMeasureDraftRequest(
            pollTypeId = 3L, electionId = e.id, title = "M", summary = "s", effectiveDate = LocalDate.now().plusDays(30)
        ))
        measures.publish(m.id, creator, confirmed = false)

        repeat(3) {
            val v = AppUserDetails(fixtures.createUser(emailPrefix = "pc-ev"))
            electionResponses.submit(v, e.id, SubmitElectionResponsesRequest(listOf(CandidateAnswerInput(cand, true, "Cand note $it", true))))
            measureResponses.submit(v, m.id, SubmitBallotResponseRequest(true, "Measure note $it", it != 1))
        }
        assertThat(electionResults.get(e.id).perCandidate.single().comments).containsExactly("Cand note 0", "Cand note 1", "Cand note 2")
        assertThat(measureResults.get(m.id).comments).containsExactly("Measure note 0", "Measure note 2")
    }
}
