package org.kodewerks.pollsystem.superadmin

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.auth.MagicLinkService
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.CreatorRequest
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.payment.FakePaymentProvider
import org.kodewerks.pollsystem.payment.FakePayments
import org.kodewerks.pollsystem.poll.QuestionAnswerInput
import org.kodewerks.pollsystem.poll.QuestionInput
import org.kodewerks.pollsystem.poll.QuestionnaireDraftRequest
import org.kodewerks.pollsystem.poll.QuestionnaireResponseController
import org.kodewerks.pollsystem.poll.QuestionnaireResultsController
import org.kodewerks.pollsystem.poll.QuestionnaireService
import org.kodewerks.pollsystem.poll.PollMilestoneTest
import org.kodewerks.pollsystem.poll.SubmitResponsesRequest
import org.kodewerks.pollsystem.repository.CreatorRequestRepository
import org.kodewerks.pollsystem.repository.QuestionRepository
import org.kodewerks.pollsystem.repository.UserRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.kodewerks.pollsystem.security.JwtTokenProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/** A super deletes an account on its owner's request (decided 2026-10-20). Test threshold k = 3. */
@AutoConfigureMockMvc
@Import(FakePayments::class, PollMilestoneTest.RecordingEmailConfig::class)
class AccountDeletionTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var tokens: JwtTokenProvider
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var payments: FakePaymentProvider
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var service: QuestionnaireService
    @Autowired private lateinit var responses: QuestionnaireResponseController
    @Autowired private lateinit var results: QuestionnaireResultsController
    @Autowired private lateinit var questions: QuestionRepository
    @Autowired private lateinit var magicLinks: MagicLinkService
    @Autowired private lateinit var creatorRequests: CreatorRequestRepository
    @Autowired private lateinit var mail: PollMilestoneTest.RecordingEmailService

    private lateinit var superUser: User

    @BeforeEach fun setUp() {
        payments.reset()
        mail.clear()
        superUser = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "del-super")
    }

    private fun bearer(u: User) = "Bearer ${tokens.generateToken(u.id, u.email)}"

    private fun delete(target: User, typed: String = target.email, by: User = superUser) =
        mockMvc.post("/api/super/users/${target.id}/delete") {
            header("Authorization", bearer(by))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(mapOf("confirmEmail" to typed))
        }

    private fun poll(creator: User = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "del-creator")): Long {
        val d = service.saveDraft(creator, QuestionnaireDraftRequest(
            pollTypeId = 2L, title = "Deletion", summary = "s", closeDate = null,
            questions = listOf(QuestionInput("Q?")), zipcodes = listOf("90001")
        ))
        service.publish(d.id, creator, confirmed = false)
        return d.id
    }

    private fun vote(pollId: Long, voter: User, comment: String? = null) {
        val q = questions.findByQuestionnaireId(pollId).single()
        responses.submit(AppUserDetails(voter), pollId, SubmitResponsesRequest(listOf(QuestionAnswerInput(q.id, "Yes", comment = comment))))
    }

    private fun rows(sql: String, id: Long) = jdbc.queryForObject(sql, Int::class.java, id)

    @Test
    fun `deletes the member and everything theirs, cancels Stripe at once, and recounts results without them`() {
        val id = poll()
        val member = users.save(fixtures.createUser(emailPrefix = "del-member").copy(stripeSubscriptionId = "sub_del"))
        vote(id, member, comment = "my comment")
        repeat(2) { vote(id, fixtures.createUser(emailPrefix = "del-other$it")) }
        magicLinks.issueToken(member)
        creatorRequests.save(CreatorRequest(user = member, reason = "please"))
        assertThat(results.get(id).visibility.mode).isEqualTo("STEPPED")          // 3 respondents = k

        delete(member).andExpect {
            status { isOk() }
            jsonPath("$.email") { value(member.email) }
            jsonPath("$.pollsRecounted") { value(1) }
            jsonPath("$.subscriptionCancelled") { value(true) }
        }

        assertThat(users.findById(member.id)).isEmpty()
        assertThat(rows("select count(*) from question_responses where user_id = ?", member.id)).isZero()
        assertThat(rows("select count(*) from result_snapshot_answers where user_id = ?", member.id)).isZero()
        assertThat(rows("select count(*) from magic_link_tokens where user_id = ?", member.id)).isZero()
        assertThat(rows("select count(*) from creator_requests where user_id = ?", member.id)).isZero()
        assertThat(payments.calls).containsExactly("cancelNow(${member.id})")
        // The person is told it's done.
        val sent = mail.sent.single { it.to == member.email }
        assertThat(sent.subject).isEqualTo("Your BallotBox account has been deleted")
        assertThat(sent.body).contains("Your account data from BallotBox.us.com has been deleted per your request.")
        assertThat(sent.body).contains("Your membership has been cancelled, and you won't be charged again.")
        // Two respondents left, under k: the results hide again.
        val after = results.get(id)
        assertThat(after.visibility.mode).isEqualTo("HIDDEN")
        assertThat(after.visibility.respondentsSoFar).isEqualTo(2)
    }

    @Test
    fun `a creator with grants but no polls can be deleted, and their grants go too`() {
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "del-c")
        delete(creator).andExpect { status { isOk() }; jsonPath("$.subscriptionCancelled") { value(false) } }
        assertThat(rows("select count(*) from role_assignments where user_id = ?", creator.id)).isZero()
        assertThat(payments.calls).isEmpty()
        assertThat(mail.sent.single { it.to == creator.email }.body).doesNotContain("membership")   // no subscription to mention
    }

    @Test
    fun `refused - wrong email typed, yourself, a super, or someone who owns polls`() {
        val member = fixtures.createUser(emailPrefix = "del-guard")
        delete(member, typed = "someone-else@test.local").andExpect { status { isBadRequest() } }
        delete(superUser).andExpect { status { isConflict() } }
        val otherSuper = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "del-super2")
        delete(otherSuper).andExpect { status { isForbidden() } }
        val owner = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "del-owner")
        poll(owner)
        delete(owner).andExpect {
            status { isConflict(); reason("Can't delete yet: this account owns 1 poll.") }
        }
        assertThat(users.findById(owner.id)).isPresent()
    }

    @Test
    fun `if Stripe can't cancel the subscription, nothing is deleted`() {
        val member = users.save(fixtures.createUser(emailPrefix = "del-fail").copy(stripeSubscriptionId = "sub_fail"))
        payments.failCancelNow = true
        delete(member).andExpect { status { isBadGateway() } }
        assertThat(users.findById(member.id)).isPresent()
        assertThat(mail.sent).isEmpty()                  // nothing deleted, so no confirmation
    }

    @Test
    fun `only a super can delete`() {
        val member = fixtures.createUser(emailPrefix = "del-noauth")
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "del-admin")
        delete(member, by = admin).andExpect { status { isForbidden() } }
        assertThat(users.findById(member.id)).isPresent()
    }

    @Test
    fun `lapsed members are found only when asked for by role`() {
        val lapsed = fixtures.createUser(access = AccessLevel.VIEWER, emailPrefix = "del-lapsed", paidUntil = null)
        mockMvc.get("/api/super/users") { header("Authorization", bearer(superUser)); param("email", lapsed.email) }
            .andExpect { status { isOk() }; jsonPath("$.length()") { value(0) } }
        mockMvc.get("/api/super/users") { header("Authorization", bearer(superUser)); param("email", lapsed.email); param("role", "VIEWER") }
            .andExpect { status { isOk() }; jsonPath("$[0].access") { value("VIEWER") } }
    }
}
