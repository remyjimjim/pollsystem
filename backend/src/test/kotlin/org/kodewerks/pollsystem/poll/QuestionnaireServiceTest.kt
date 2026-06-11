package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.repository.QuestionRepository
import org.kodewerks.pollsystem.repository.QuestionnaireDomainRepository
import org.kodewerks.pollsystem.repository.QuestionnaireRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.temporal.ChronoUnit

class QuestionnaireServiceTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: QuestionnaireService
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var questionnaires: QuestionnaireRepository
    @Autowired private lateinit var questions: QuestionRepository
    @Autowired private lateinit var domains: QuestionnaireDomainRepository

    private val questionnairePollTypeId = 2L  // V1 seed: id=2 is Questionnaire

    @Test
    fun `save draft persists questions and domains`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")

        val saved = service.saveDraft(
            creator,
            QuestionnaireDraftRequest(
                pollTypeId = questionnairePollTypeId,
                title = "Local park hours",
                summary = "Should park hours be extended?",
                closeDate = Instant.now().plus(10, ChronoUnit.DAYS),
                questions = listOf(QuestionInput("Should hours be extended?")),
                zipcodes = listOf("90001")
            )
        )

        assertThat(saved.status).isEqualTo(PollStatus.DRAFT)
        assertThat(questions.findByQuestionnaireId(saved.id)).hasSize(1)
        assertThat(domains.findByQuestionnaireId(saved.id)).hasSize(1)
    }

    @Test
    fun `update replaces questions and domains for a DRAFT`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val initial = service.saveDraft(
            creator,
            QuestionnaireDraftRequest(
                pollTypeId = questionnairePollTypeId,
                title = "v1",
                summary = "v1 summary",
                questions = listOf(QuestionInput("Q1")),
                zipcodes = listOf("90001")
            )
        )

        service.update(
            initial.id,
            creator,
            QuestionnaireDraftRequest(
                pollTypeId = questionnairePollTypeId,
                title = "v2",
                summary = "v2 summary",
                questions = listOf(QuestionInput("Q1 revised"), QuestionInput("Q2 added")),
                zipcodes = listOf("90001", "90012")
            )
        )

        val refreshed = questionnaires.findById(initial.id).orElseThrow()
        assertThat(refreshed.title).isEqualTo("v2")
        assertThat(questions.findByQuestionnaireId(initial.id).map { it.question })
            .containsExactlyInAnyOrder("Q1 revised", "Q2 added")
        assertThat(domains.findByQuestionnaireId(initial.id).map { it.zipcode })
            .containsExactlyInAnyOrder("90001", "90012")
    }

    @Test
    fun `publish with close_date less than 5 days ahead requires confirmation`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val draft = service.saveDraft(
            creator,
            QuestionnaireDraftRequest(
                pollTypeId = questionnairePollTypeId,
                title = "soon-to-close",
                summary = "summary",
                closeDate = Instant.now().plus(2, ChronoUnit.DAYS),
                questions = listOf(QuestionInput("Q1")),
                zipcodes = listOf("90001")
            )
        )

        assertThatThrownBy { service.publish(draft.id, creator, confirmed = false) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode.value()).isEqualTo(422)
                assertThat(it.reason).startsWith("close_date_short:")
            }

        // confirmed=true should succeed
        val published = service.publish(draft.id, creator, confirmed = true)
        assertThat(published.status).isEqualTo(PollStatus.PUBLISHED)
    }

    @Test
    fun `publish without close_date succeeds`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val draft = service.saveDraft(
            creator,
            QuestionnaireDraftRequest(
                pollTypeId = questionnairePollTypeId,
                title = "open-ended",
                summary = "summary",
                closeDate = null,
                questions = listOf(QuestionInput("Q1")),
                zipcodes = listOf("90001")
            )
        )

        val published = service.publish(draft.id, creator, confirmed = false)
        assertThat(published.status).isEqualTo(PollStatus.PUBLISHED)
    }

    @Test
    fun `update is rejected once published`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val draft = service.saveDraft(
            creator,
            QuestionnaireDraftRequest(
                pollTypeId = questionnairePollTypeId,
                title = "title",
                summary = "summary",
                questions = listOf(QuestionInput("Q1")),
                zipcodes = listOf("90001")
            )
        )
        service.publish(draft.id, creator, confirmed = false)

        assertThatThrownBy {
            service.update(
                draft.id,
                creator,
                QuestionnaireDraftRequest(
                    pollTypeId = questionnairePollTypeId,
                    title = "modified",
                    summary = "modified",
                    questions = listOf(QuestionInput("Q1")),
                    zipcodes = listOf("90001")
                )
            )
        }.isInstanceOfSatisfying(ResponseStatusException::class.java) {
            assertThat(it.statusCode.value()).isEqualTo(409)
        }
    }
}
