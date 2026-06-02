package com.pollsystem.superadmin

import com.pollsystem.AbstractIntegrationTest
import com.pollsystem.TestFixtures
import com.pollsystem.email.EmailService
import com.pollsystem.model.AccessLevel
import com.pollsystem.poll.BallotMeasureDraftRequest
import com.pollsystem.poll.BallotMeasureResponseController
import com.pollsystem.poll.BallotMeasureService
import com.pollsystem.poll.CandidateAnswerInput
import com.pollsystem.poll.CandidateInput
import com.pollsystem.poll.ElectionDraftRequest
import com.pollsystem.poll.ElectionResponseController
import com.pollsystem.poll.ElectionService
import com.pollsystem.poll.QuestionAnswerInput
import com.pollsystem.poll.QuestionInput
import com.pollsystem.poll.QuestionnaireDraftRequest
import com.pollsystem.poll.QuestionnaireResponseController
import com.pollsystem.poll.QuestionnaireService
import com.pollsystem.poll.SubmitBallotResponseRequest
import com.pollsystem.poll.SubmitElectionResponsesRequest
import com.pollsystem.poll.SubmitResponsesRequest
import com.pollsystem.repository.CandidateRepository
import com.pollsystem.repository.QuestionRepository
import com.pollsystem.repository.UserMessageRepository
import com.pollsystem.repository.UserRepository
import com.pollsystem.security.AppUserDetails
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Import
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

@Import(SuperUsersControllerTest.RecordingEmailConfig::class)
class SuperUsersControllerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var controller: SuperUsersController
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var userMessages: UserMessageRepository
    @Autowired private lateinit var roleAssignments: com.pollsystem.repository.RoleAssignmentRepository
    @Autowired private lateinit var recordedEmails: RecordingEmailService
    @Autowired private lateinit var questionnaireService: QuestionnaireService
    @Autowired private lateinit var questionnaireResponseController: QuestionnaireResponseController
    @Autowired private lateinit var questions: QuestionRepository
    @Autowired private lateinit var electionService: ElectionService
    @Autowired private lateinit var electionResponseController: ElectionResponseController
    @Autowired private lateinit var candidates: CandidateRepository
    @Autowired private lateinit var ballotMeasureService: BallotMeasureService
    @Autowired private lateinit var ballotMeasureResponseController: BallotMeasureResponseController

    private fun principalFor(user: com.pollsystem.model.User) = AppUserDetails(user)

    @Test
    fun `list defaults to enabled User+Creator+Admin, excludes SUPER and VIEWER`() {
        fixtures.createUser(access = AccessLevel.USER, emailPrefix = "u")
        fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "c")
        fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "a")
        fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "s")
        fixtures.createUser(access = AccessLevel.VIEWER, emailPrefix = "v")

        val rows = controller.list(null, false, null, null, null, null, null)
        val accesses = rows.map { it.access }.toSet()
        assertThat(accesses).contains(AccessLevel.USER, AccessLevel.CREATOR, AccessLevel.ADMIN)
        assertThat(accesses).doesNotContain(AccessLevel.SUPER, AccessLevel.VIEWER)
    }

    @Test
    fun `list role param narrows the result set`() {
        fixtures.createUser(access = AccessLevel.USER, emailPrefix = "u")
        fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "a")

        val only = controller.list(listOf("ADMIN"), false, null, null, null, null, null)
        assertThat(only).allMatch { it.access == AccessLevel.ADMIN }
    }

    @Test
    fun `list excludes disabled by default and includes them when flag is set`() {
        val u = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "dis")
        users.save(u.copy(isEnabled = false))

        val without = controller.list(listOf("USER"), false, "dis", null, null, null, null)
        assertThat(without.none { it.id == u.id }).isTrue()
        val withDisabled = controller.list(listOf("USER"), true, "dis", null, null, null, null)
        assertThat(withDisabled.any { it.id == u.id }).isTrue()
    }

    @Test
    fun `list filters by email substring case-insensitively`() {
        val u = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "Filterme")
        val rows = controller.list(listOf("USER"), false, "FILTERME", null, null, null, null)
        assertThat(rows.any { it.id == u.id }).isTrue()
    }

    @Test
    fun `list filters by zipcode and derives state and county from the zip`() {
        val u = fixtures.createUser(access = AccessLevel.USER, zipcode = "90001", emailPrefix = "geo")
        val rows = controller.list(listOf("USER"), false, "geo", null, null, listOf("90001"), null)
        assertThat(rows.single { it.id == u.id }).satisfies({
            assertThat(it.zipcode).isEqualTo("90001")
            assertThat(it.stateInitial).isEqualTo("CA")
            assertThat(it.countyName).isEqualTo("Los Angeles")
        })
    }

    @Test
    fun `toggleEnabled flips the flag and rejects SUPER`() {
        val u = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "tog")
        val before = users.findById(u.id).orElseThrow().isEnabled
        val after = controller.toggleEnabled(u.id)
        assertThat(after.isEnabled).isEqualTo(!before)

        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "noflip")
        assertThatThrownBy { controller.toggleEnabled(sup.id) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode.value()).isEqualTo(409)
            }
    }

    @Test
    fun `bulkToggle disables only listable roles, leaves SUPER untouched`() {
        val a = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "ba")
        val b = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "bb")
        val s = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "bs")

        controller.bulkToggle(BulkToggleRequest(listOf(a.id, b.id, s.id), enable = false))
        assertThat(users.findById(a.id).orElseThrow().isEnabled).isFalse()
        assertThat(users.findById(b.id).orElseThrow().isEnabled).isFalse()
        // SUPER is filtered out by LISTABLE_ROLES — must remain enabled.
        assertThat(users.findById(s.id).orElseThrow().isEnabled).isTrue()
    }

    @Test
    fun `edit updates role and propagates onto every RoleAssignment`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "edit-role")
        fixtures.assignAdmin(admin)

        val after = controller.edit(admin.id, EditUserRequest(role = "CREATOR", zipcode = null))
        assertThat(after.access).isEqualTo(AccessLevel.CREATOR)

        // RoleAssignmentBulkOps already flushed + cleared the persistence
        // context, so a fresh read shows the new role.
        val rows = roleAssignments.findByUserId(admin.id)
        assertThat(rows).isNotEmpty
        assertThat(rows.map { it.role }.toSet()).containsExactly(AccessLevel.CREATOR)
    }

    @Test
    fun `edit updates zipcode and re-derives state and county`() {
        val u = fixtures.createUser(access = AccessLevel.USER, zipcode = "90001", emailPrefix = "edit-zip")
        val after = controller.edit(u.id, EditUserRequest(role = null, zipcode = "10001"))
        assertThat(after.zipcode).isEqualTo("10001")
        assertThat(after.stateInitial).isEqualTo("NY")
    }

    @Test
    fun `edit rejects SUPER targets, unknown zipcodes, and bad roles`() {
        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "edit-super")
        assertThatThrownBy { controller.edit(sup.id, EditUserRequest(role = "USER", zipcode = null)) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode.value()).isEqualTo(409)
            }

        val u = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "edit-bad")
        assertThatThrownBy { controller.edit(u.id, EditUserRequest(role = null, zipcode = "00000")) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode.value()).isEqualTo(400)
            }
        assertThatThrownBy { controller.edit(u.id, EditUserRequest(role = "SUPER", zipcode = null)) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode.value()).isEqualTo(400)
            }
    }

    @Test
    fun `demote walks Admin to Creator and Creator to User, rejects User and Super`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "demote-admin")
        val afterAdmin = controller.demote(admin.id)
        assertThat(afterAdmin.access).isEqualTo(AccessLevel.CREATOR)

        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "demote-creator")
        val afterCreator = controller.demote(creator.id)
        assertThat(afterCreator.access).isEqualTo(AccessLevel.USER)

        val plain = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "alreadyuser")
        assertThatThrownBy { controller.demote(plain.id) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode.value()).isEqualTo(409)
            }

        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "supercannot")
        assertThatThrownBy { controller.demote(sup.id) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode.value()).isEqualTo(403)
            }
    }

    @Test
    fun `polls-created lists author's questionnaires and polls-completed + answers surface respondent's work`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "polls-creator")
        val voter = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "polls-voter")
        val bystander = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "polls-bystander")

        // Publish a 2-question questionnaire owned by `creator`.
        val draft = questionnaireService.saveDraft(
            creator,
            QuestionnaireDraftRequest(
                pollTypeId = 2L,
                title = "Spring 2026 pulse",
                summary = "summary",
                closeDate = null,
                questions = listOf(QuestionInput("Coffee?"), QuestionInput("Tea?")),
                zipcodes = listOf("90001")
            )
        )
        questionnaireService.publish(draft.id, creator, confirmed = false)
        val qList = questions.findByQuestionnaireId(draft.id)

        // The voter answers both questions; the bystander does not.
        questionnaireResponseController.submit(
            AppUserDetails(voter),
            draft.id,
            SubmitResponsesRequest(answers = listOf(
                QuestionAnswerInput(qList[0].id, "Yes"),
                QuestionAnswerInput(qList[1].id, "No")
            ))
        )

        // Creator's polls-created lists the questionnaire exactly once.
        val created = controller.pollsCreated(creator.id)
        assertThat(created).hasSize(1)
        assertThat(created[0].type).isEqualTo("questionnaire")
        assertThat(created[0].id).isEqualTo(draft.id)
        assertThat(created[0].title).isEqualTo("Spring 2026 pulse")
        // Bystander created nothing.
        assertThat(controller.pollsCreated(bystander.id)).isEmpty()

        // Voter's polls-completed lists the questionnaire exactly once
        // (deduped across 2 question responses).
        val completed = controller.pollsCompleted(voter.id)
        assertThat(completed).hasSize(1)
        assertThat(completed[0].type).isEqualTo("questionnaire")
        assertThat(completed[0].id).isEqualTo(draft.id)
        // Bystander completed nothing.
        assertThat(controller.pollsCompleted(bystander.id)).isEmpty()

        // Answer detail returns one row per question with the typed answer.
        val answers = controller.pollAnswers(voter.id, "questionnaire", draft.id)
        assertThat(answers).hasSize(2)
        assertThat(answers.map { it.prompt }).containsExactlyInAnyOrder("Coffee?", "Tea?")
        assertThat(answers.map { it.answer }).containsExactlyInAnyOrder("Yes", "No")

        // 404 on unknown user, 400 on unknown type.
        assertThatThrownBy { controller.pollsCreated(99_999) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode.value()).isEqualTo(404)
            }
        assertThatThrownBy { controller.pollAnswers(voter.id, "nope", draft.id) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode.value()).isEqualTo(400)
            }
    }

    @Test
    fun `polls-created lists author's election and polls-completed + answers surface voter's candidate picks`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "polls-ecreator")
        val voter = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "polls-evoter")

        val electionDraft = electionService.saveDraft(
            creator,
            ElectionDraftRequest(
                pollTypeId = 1L,
                title = "Sample mayoral race",
                date = LocalDate.now().plusDays(30),
                zipcode = "90001",
                candidates = listOf(
                    CandidateInput("Alice", "Indep", "Mayor"),
                    CandidateInput("Bob", "Indep", "Mayor")
                )
            )
        )
        electionService.publish(electionDraft.id, creator, confirmed = false)
        val cs = candidates.findByElectionId(electionDraft.id)

        electionResponseController.submit(
            AppUserDetails(voter),
            electionDraft.id,
            SubmitElectionResponsesRequest(answers = cs.map {
                CandidateAnswerInput(it.id, response = it.name == "Alice")
            })
        )

        val created = controller.pollsCreated(creator.id)
        assertThat(created.map { it.type to it.id })
            .containsExactly("election" to electionDraft.id)

        val completed = controller.pollsCompleted(voter.id)
        assertThat(completed).hasSize(1)
        assertThat(completed[0].type).isEqualTo("election")
        assertThat(completed[0].id).isEqualTo(electionDraft.id)

        val answers = controller.pollAnswers(voter.id, "election", electionDraft.id)
        assertThat(answers).hasSize(2)
        val byPrompt = answers.associateBy { it.prompt }
        assertThat(byPrompt["Alice (Indep)"]?.answer).isEqualTo("Yes")
        assertThat(byPrompt["Bob (Indep)"]?.answer).isEqualTo("No")
    }

    @Test
    fun `polls-created lists author's ballot measure and polls-completed + answers surface voter's yes-no`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "polls-bcreator")
        val voter = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "polls-bvoter")

        // A ballot measure must hang off a published Election owned by the
        // same creator; publish both so the response controller accepts a
        // vote against the measure.
        val parentElection = electionService.saveDraft(
            creator,
            ElectionDraftRequest(
                pollTypeId = 1L,
                title = "Parent election",
                date = LocalDate.now().plusDays(30),
                zipcode = "90001",
                candidates = listOf(CandidateInput("X", "Y", "Mayor"))
            )
        )
        electionService.publish(parentElection.id, creator, confirmed = false)

        val measureDraft = ballotMeasureService.saveDraft(
            creator,
            BallotMeasureDraftRequest(
                pollTypeId = 3L,
                electionId = parentElection.id,
                title = "Approve $1M bond?",
                summary = "Funds local infrastructure.",
                effectiveDate = LocalDate.now().plusDays(60)
            )
        )
        ballotMeasureService.publish(measureDraft.id, creator, confirmed = false)

        ballotMeasureResponseController.submit(
            AppUserDetails(voter),
            measureDraft.id,
            SubmitBallotResponseRequest(response = true, comment = "needs funding")
        )

        // The creator now owns 1 election + 1 ballot measure.
        val created = controller.pollsCreated(creator.id)
        val createdTypes = created.map { it.type }.sorted()
        assertThat(createdTypes).containsExactly("ballot-measure", "election")

        val completed = controller.pollsCompleted(voter.id)
        assertThat(completed).hasSize(1)
        assertThat(completed[0].type).isEqualTo("ballot-measure")
        assertThat(completed[0].id).isEqualTo(measureDraft.id)

        val answers = controller.pollAnswers(voter.id, "ballot-measure", measureDraft.id)
        assertThat(answers).hasSize(1)
        assertThat(answers[0].prompt).isEqualTo("Approve $1M bond?")
        assertThat(answers[0].answer).isEqualTo("Yes")
        assertThat(answers[0].comment).isEqualTo("needs funding")
    }

    @Test
    fun `createMessage saves and optionally emails, editMessage updates body`() {
        val recipient = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "msg")
        val super1 = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "supermsg")

        recordedEmails.clear()
        val created = controller.createMessage(
            recipient.id,
            CreateMessageRequest(body = "hello there", sendEmail = true),
            principalFor(super1)
        )
        assertThat(created.body).isEqualTo("hello there")
        assertThat(created.emailed).isTrue()
        assertThat(recordedEmails.sent).hasSize(1)
        assertThat(recordedEmails.sent[0].to).isEqualTo(recipient.email)

        val edited = controller.editMessage(created.id, EditMessageRequest("revised"))
        assertThat(edited.body).isEqualTo("revised")
        // Editing should never re-email.
        assertThat(recordedEmails.sent).hasSize(1)

        val history = controller.listMessages(recipient.id)
        assertThat(history).hasSize(1)
        assertThat(history[0].body).isEqualTo("revised")
    }

    @Test
    fun `createMessage rejects empty and over-2000 char bodies`() {
        val recipient = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "bad")
        val super1 = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "supbad")
        assertThatThrownBy {
            controller.createMessage(recipient.id, CreateMessageRequest("   ", false), principalFor(super1))
        }.isInstanceOfSatisfying(ResponseStatusException::class.java) {
            assertThat(it.statusCode.value()).isEqualTo(400)
        }
        assertThatThrownBy {
            controller.createMessage(recipient.id, CreateMessageRequest("x".repeat(2001), false), principalFor(super1))
        }.isInstanceOfSatisfying(ResponseStatusException::class.java) {
            assertThat(it.statusCode.value()).isEqualTo(400)
        }
    }

    @Test
    fun `emailSuggestions returns prefix matches capped at 20`() {
        // The pool can contain many seeded users; assert specific emails are present.
        fixtures.createUser(emailPrefix = "auto-zzz1")
        fixtures.createUser(emailPrefix = "auto-zzz2")
        val hits = controller.emailSuggestions("auto-zzz")
        assertThat(hits.any { it.startsWith("auto-zzz1") }).isTrue()
        assertThat(hits.any { it.startsWith("auto-zzz2") }).isTrue()
        assertThat(controller.emailSuggestions("   ")).isEmpty()
    }

    @Test
    fun `list with latest message attaches it to the row`() {
        val recipient = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "latest")
        val super1 = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "suplatest")
        controller.createMessage(recipient.id, CreateMessageRequest("first", false), principalFor(super1))
        controller.createMessage(recipient.id, CreateMessageRequest("second", false), principalFor(super1))
        val row = controller.list(listOf("USER"), false, "latest", null, null, null, null).single { it.id == recipient.id }
        assertThat(row.latestMessage?.body).isEqualTo("second")
        assertThat(userMessages.findByUserIdOrderByCreatedAtDesc(recipient.id)).hasSize(2)
    }

    @Test
    fun `list message filter narrows to users with a matching message body`() {
        val hit = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "msgmatch")
        val miss = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "msgmiss")
        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "supmsg")
        controller.createMessage(hit.id,
            CreateMessageRequest("Please review the QUARANTINE policy", false), principalFor(sup))
        controller.createMessage(miss.id,
            CreateMessageRequest("Welcome to the platform", false), principalFor(sup))

        val rows = controller.list(listOf("USER"), false, null, null, null, null, "quarantine")
        val ids = rows.map { it.id }.toSet()
        assertThat(ids).contains(hit.id)
        assertThat(ids).doesNotContain(miss.id)

        // Blank needle is treated as no filter — both users pass.
        val all = controller.list(listOf("USER"), false, null, null, null, null, "   ").map { it.id }.toSet()
        assertThat(all).contains(hit.id, miss.id)
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
        override fun send(to: String, subject: String, body: String) {
            sent += Sent(to, subject, body)
        }
        fun clear() = sent.clear()
    }
}
