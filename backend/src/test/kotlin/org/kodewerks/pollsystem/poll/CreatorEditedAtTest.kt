package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.adminpolls.AdminPollEditRequest
import org.kodewerks.pollsystem.adminpolls.AdminPollsController
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.repository.QuestionnaireRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.temporal.ChronoUnit

/** creator_edited_at tracks the creator's own writes, not admin moderation. */
class CreatorEditedAtTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: QuestionnaireService
    @Autowired private lateinit var creatorPolls: CreatorPollsController
    @Autowired private lateinit var adminPolls: AdminPollsController
    @Autowired private lateinit var questionnaires: QuestionnaireRepository
    @Autowired private lateinit var fixtures: TestFixtures

    private fun draft(title: String) = QuestionnaireDraftRequest(
        pollTypeId = 2L, title = title, summary = "s",
        questions = listOf(QuestionInput("Q?")), zipcodes = listOf("90001")
    )

    /** Rewind the stamp so a later write is distinguishable without sleeping. */
    private fun rewind(id: Long): Instant {
        val past = Instant.now().minus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS)
        questionnaires.save(questionnaires.findById(id).get().copy(creatorEditedAt = past))
        return past
    }
    private fun stamp(id: Long) = questionnaires.findById(id).get().creatorEditedAt

    @Test
    fun `creator update, publish, archive and restore bump the stamp`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "edited")
        val q = service.saveDraft(creator, draft("v1"))

        var past = rewind(q.id)
        service.update(q.id, creator, draft("v2"))
        assertThat(stamp(q.id)).isAfter(past)

        past = rewind(q.id)
        service.publish(q.id, creator, confirmed = true)
        assertThat(stamp(q.id)).isAfter(past)

        past = rewind(q.id)
        creatorPolls.delete(AppUserDetails(creator), "questionnaire", q.id)
        assertThat(stamp(q.id)).isAfter(past)

        past = rewind(q.id)
        creatorPolls.restore(AppUserDetails(creator), "questionnaire", q.id)
        assertThat(stamp(q.id)).isAfter(past)
    }

    @Test
    fun `admin moderation leaves the stamp alone`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "edited")
        val q = service.saveDraft(creator, draft("v1"))
        service.publish(q.id, creator, confirmed = true)
        val past = rewind(q.id)

        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "editedsuper")
        adminPolls.editPoll(
            "QUESTIONNAIRE", q.id,
            AdminPollEditRequest(status = PollStatus.CLOSED, reason = "moderation"),
            AppUserDetails(sup)
        )

        assertThat(questionnaires.findById(q.id).get().status).isEqualTo(PollStatus.CLOSED)
        assertThat(stamp(q.id)).isEqualTo(past)
    }
}
