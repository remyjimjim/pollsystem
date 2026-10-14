package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.email.EmailService
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.QuestionRepository
import org.kodewerks.pollsystem.repository.QuestionnaireRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Duration
import java.time.Instant

/** Creator reports, phase 3: milestone emails, each at most once per poll (test k = 3). */
class PollMilestoneTest : AbstractIntegrationTest() {

    @Autowired private lateinit var service: QuestionnaireService
    @Autowired private lateinit var questionnaireRepo: QuestionnaireRepository
    @Autowired private lateinit var responses: QuestionnaireResponseController
    @Autowired private lateinit var questions: QuestionRepository
    @Autowired private lateinit var snapshots: ResultSnapshotService
    @Autowired private lateinit var milestones: PollMilestoneService
    @Autowired private lateinit var mail: RecordingEmailService
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var snapshotRows: org.kodewerks.pollsystem.repository.ResultSnapshotRepository
    @Autowired private lateinit var notificationRows: org.kodewerks.pollsystem.repository.PollNotificationRepository

    @BeforeEach fun clearMail() = mail.clear()

    private fun poll(creator: User): Long {
        val d = service.saveDraft(creator, QuestionnaireDraftRequest(
            pollTypeId = 2L, title = "Milestones", summary = "s", closeDate = null,
            questions = listOf(QuestionInput("Q?")), zipcodes = listOf("90001")
        ))
        service.publish(d.id, creator, confirmed = false)
        return d.id
    }

    private fun vote(pollId: Long, i: Int) {
        val q = questions.findByQuestionnaireId(pollId).single()
        val voter = fixtures.createUser(emailPrefix = "ms-v$i")
        responses.submit(AppUserDetails(voter), pollId, SubmitResponsesRequest(listOf(QuestionAnswerInput(q.id, "Yes"))))
    }

    private fun sentTo(user: User) = mail.sent.filter { it.to == user.email }

    @Test
    fun `the creator hears once that results are visible, when the k-th person responds`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "ms-creator")
        val id = poll(creator)
        vote(id, 1); vote(id, 2)
        assertThat(sentTo(creator)).isEmpty()

        vote(id, 3)                                     // 3rd = k: the first snapshot
        val first = sentTo(creator).single()
        assertThat(first.subject).isEqualTo("Your poll \"Milestones\" has 3 responses: results are now visible")
        assertThat(first.body).contains("/polls/questionnaire/$id/results").contains("/creator/polls/questionnaire/$id/report")

        repeat(3) { vote(id, 10 + it) }                 // the next step: no second email
        assertThat(sentTo(creator)).hasSize(1)
    }

    @Test
    fun `the startup backfill creates a first snapshot without emailing`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "ms-creator")
        val id = poll(creator)
        repeat(3) { vote(id, it) }
        // As if these answers predated snapshots: no snapshot, no email sent yet.
        snapshotRows.delete(snapshotRows.findByPollTypeAndPollId(PollKind.QUESTIONNAIRE, id)!!)
        notificationRows.deleteAll(notificationRows.findAll().filter { it.pollId == id })
        mail.clear()

        snapshots.refresh(PollKind.QUESTIONNAIRE, id, notify = false)   // the backfill
        assertThat(snapshotRows.findByPollTypeAndPollId(PollKind.QUESTIONNAIRE, id)).isNotNull()
        assertThat(mail.sent).isEmpty()
    }

    @Test
    fun `a poll that just closed gets one final report, a long-closed one none`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "ms-creator")
        val recent = poll(creator)
        repeat(4) { vote(recent, it) }
        val old = poll(creator)
        mail.clear()
        fun closeAt(id: Long, at: Instant) = questionnaireRepo.save(questionnaireRepo.findById(id).get().copy(closeDate = at))
        closeAt(recent, Instant.now().minus(Duration.ofHours(1)))
        closeAt(old, Instant.now().minus(Duration.ofDays(10)))

        milestones.sendClosingReports()
        val report = sentTo(creator).single()
        assertThat(report.subject).isEqualTo("Your poll \"Milestones\" has closed")
        assertThat(report.body).contains("Respondents: 4").contains("/creator/polls/questionnaire/$recent/report")

        milestones.sendClosingReports()                 // already sent: nothing new
        assertThat(sentTo(creator)).hasSize(1)
    }

    @TestConfiguration
    class RecordingEmailConfig {
        @Bean
        @Primary
        fun recordingEmailService() = RecordingEmailService()
    }

    class RecordingEmailService : EmailService {
        data class Sent(val to: String, val subject: String, val body: String)
        val sent = mutableListOf<Sent>()
        override fun send(to: String, subject: String, body: String) { sent += Sent(to, subject, body) }
        fun clear() = sent.clear()
    }
}
