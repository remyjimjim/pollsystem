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

    // ---------- the Creator dashboard's Emails checkbox ----------

    @Autowired private lateinit var creatorPolls: CreatorPollsController

    private fun emailsOn(creator: User, id: Long) =
        creatorPolls.list(AppUserDetails(creator), showArchived = false).single { it.id == id }.emails

    @Test
    fun `paused emails are skipped, not caught up, and resuming only affects later ones`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "ms-creator")
        val id = poll(creator)
        assertThat(emailsOn(creator, id)).isTrue()      // on by default

        creatorPolls.setEmails(AppUserDetails(creator), "questionnaire", id, SetEmailsRequest(false))
        assertThat(emailsOn(creator, id)).isFalse()
        repeat(3) { vote(id, 30 + it) }                 // the 10-response milestone (k = 3) while paused
        assertThat(sentTo(creator)).isEmpty()

        creatorPolls.setEmails(AppUserDetails(creator), "questionnaire", id, SetEmailsRequest(true))
        repeat(3) { vote(id, 40 + it) }                 // resumed: the skipped one isn't sent later
        assertThat(sentTo(creator)).isEmpty()

        // A later milestone after resuming is sent.
        questionnaireRepo.save(questionnaireRepo.findById(id).get().copy(closeDate = Instant.now().minus(Duration.ofHours(1))))
        milestones.sendClosingReports()
        assertThat(sentTo(creator).single().subject).isEqualTo("Your poll \"Milestones\" has closed")
    }

    @Test
    fun `a closing report missed while paused isn't sent after resuming`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "ms-creator")
        val id = poll(creator)
        creatorPolls.setEmails(AppUserDetails(creator), "questionnaire", id, SetEmailsRequest(false))
        questionnaireRepo.save(questionnaireRepo.findById(id).get().copy(closeDate = Instant.now().minus(Duration.ofHours(1))))
        milestones.sendClosingReports()
        creatorPolls.setEmails(AppUserDetails(creator), "questionnaire", id, SetEmailsRequest(true))
        milestones.sendClosingReports()
        assertThat(sentTo(creator)).isEmpty()
    }

    @Test
    fun `only the poll's creator can pause its emails`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "ms-creator")
        val id = poll(creator)
        val other = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "ms-other")
        org.assertj.core.api.Assertions.assertThatThrownBy {
            creatorPolls.setEmails(AppUserDetails(other), "questionnaire", id, SetEmailsRequest(false))
        }.isInstanceOfSatisfying(org.springframework.web.server.ResponseStatusException::class.java) {
            assertThat(it.statusCode.value()).isEqualTo(403)
        }
        assertThat(emailsOn(creator, id)).isTrue()
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
