package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.CandidateResponse
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.model.QuestionResponse
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.CandidateRepository
import org.kodewerks.pollsystem.repository.CandidateResponseRepository
import org.kodewerks.pollsystem.repository.ElectionRepository
import org.kodewerks.pollsystem.repository.QuestionRepository
import org.kodewerks.pollsystem.repository.QuestionResponseRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate

/** "View past ballots" (2026-10-28). */
class MyBallotsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var controller: MyBallotsController
    @Autowired private lateinit var elections: ElectionService
    @Autowired private lateinit var electionRepo: ElectionRepository
    @Autowired private lateinit var questionnaires: QuestionnaireService
    @Autowired private lateinit var candidates: CandidateRepository
    @Autowired private lateinit var candidateResponses: CandidateResponseRepository
    @Autowired private lateinit var questions: QuestionRepository
    @Autowired private lateinit var questionResponses: QuestionResponseRepository
    @Autowired private lateinit var fixtures: TestFixtures

    private fun election(c: User, title: String, candidate: String, office: String, includes: List<Long>? = null): Long {
        val d = elections.saveDraft(c, ElectionDraftRequest(
            pollTypeId = 1L, title = title, date = LocalDate.now().plusDays(10), scopeLevel = ScopeLevel.NATIONAL,
            candidates = listOf(CandidateInput(candidate, "Ind", office)), includedElectionIds = includes))
        elections.publish(d.id, c, confirmed = true)
        return d.id
    }

    private fun vote(u: User, electionId: Long) {
        candidateResponses.save(CandidateResponse(user = u, candidate = candidates.findByElectionId(electionId).first(), response = true))
    }

    @Test
    fun `lists what I answered, newest first, a district ballot once with its statewide races folded in`() {
        val c = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "mb-creator")
        val me = fixtures.createUser(emailPrefix = "mb-voter")
        val someoneElse = fixtures.createUser(emailPrefix = "mb-other")
        val statewide = election(c, "Mb statewide", "Sam", "U.S. Senator")
        val district = election(c, "Mb District 1", "Hal", "U.S. Representative, District 1", listOf(statewide))
        val q = questionnaires.saveDraft(c, QuestionnaireDraftRequest(pollTypeId = 2L, title = "Mb park hours", summary = "s",
            closeDate = null, questions = listOf(QuestionInput("Later?")), zipcodes = listOf("90001")))
        questionnaires.publish(q.id, c, confirmed = true)

        vote(me, district); vote(me, statewide)        // one ballot page, two elections
        questionResponses.save(QuestionResponse(question = questions.findByQuestionnaireId(q.id).first(), response = "Yes", user = me))
        vote(someoneElse, district)

        val mine = controller.mine(AppUserDetails(me))
        assertThat(mine.map { it.type to it.title }).containsExactly("questionnaire" to "Mb park hours", "election" to "Mb District 1")
        assertThat(mine.all { it.open }).isTrue()

        // Closed polls stay listed, marked not open.
        electionRepo.save(electionRepo.findById(district).get().copy(status = PollStatus.CLOSED, closeDate = Instant.now().minusSeconds(60)))
        assertThat(controller.mine(AppUserDetails(me)).single { it.id == district }.open).isFalse()

        assertThat(controller.mine(AppUserDetails(fixtures.createUser(emailPrefix = "mb-none")))).isEmpty()
    }
}
