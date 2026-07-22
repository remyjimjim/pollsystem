package org.kodewerks.pollsystem.auth

import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.poll.QuestionAnswerInput
import org.kodewerks.pollsystem.poll.QuestionInput
import org.kodewerks.pollsystem.poll.QuestionnaireDraftRequest
import org.kodewerks.pollsystem.poll.QuestionnaireResponseController
import org.kodewerks.pollsystem.poll.QuestionnaireService
import org.kodewerks.pollsystem.poll.SubmitResponsesRequest
import org.kodewerks.pollsystem.repository.QuestionRepository
import org.kodewerks.pollsystem.repository.UserRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * Phase 3 of paid onboarding: the complete-profile endpoint and the
 * participation guard that depends on it. A payment-first user is provisioned
 * with only an email (null phone/zipcode); they must complete their profile
 * before submitting responses.
 */
class CompleteProfileTest : AbstractIntegrationTest() {

    @Autowired private lateinit var auth: AuthController
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var questionnaireService: QuestionnaireService
    @Autowired private lateinit var responseController: QuestionnaireResponseController
    @Autowired private lateinit var questions: QuestionRepository

    /** A payment-first user: email only, no phone/zipcode. */
    private fun incompleteUser(email: String = "paidfirst@test.local"): User =
        users.save(User(email = email, access = AccessLevel.USER, isEnabled = true))

    @Test
    fun `complete-profile sets phone + zipcode and marks the profile complete`() {
        val user = incompleteUser()
        assertThat(user.profileComplete).isFalse

        val dto = auth.completeProfile(
            AppUserDetails(user),
            CompleteProfileRequest(phone = "+15550009001", zipcode = "90001"),
        )

        assertThat(dto.profileComplete).isTrue
        assertThat(dto.phone).isEqualTo("+15550009001")
        assertThat(dto.zipcode).isEqualTo("90001")
        val reloaded = users.findById(user.id).get()
        assertThat(reloaded.phone).isEqualTo("+15550009001")
        assertThat(reloaded.zipcode).isEqualTo("90001")
    }

    @Test
    fun `complete-profile rejects an unknown zipcode`() {
        val user = incompleteUser("badzip@test.local")
        assertThatThrownBy {
            auth.completeProfile(
                AppUserDetails(user),
                CompleteProfileRequest(phone = "+15550009002", zipcode = "00000"),
            )
        }
            .isInstanceOf(ResponseStatusException::class.java)
            .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `complete-profile rejects a phone already held by another account`() {
        // An existing complete user owns this phone.
        fixtures.createUser(zipcode = "90001", emailPrefix = "owner").let {
            users.save(it.copy(phone = "+15550009003"))
        }
        val user = incompleteUser("collide@test.local")

        assertThatThrownBy {
            auth.completeProfile(
                AppUserDetails(user),
                CompleteProfileRequest(phone = "+15550009003", zipcode = "90001"),
            )
        }
            .isInstanceOf(ResponseStatusException::class.java)
            .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT)
    }

    @Test
    fun `an incomplete user cannot submit a response`() {
        // A creator publishes a questionnaire.
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "creator")
        val draft = questionnaireService.saveDraft(
            creator,
            QuestionnaireDraftRequest(
                pollTypeId = 2L,
                title = "Guarded poll",
                summary = "s",
                closeDate = null,
                questions = listOf(QuestionInput("Should we?")),
                zipcodes = listOf("90001"),
            ),
        )
        questionnaireService.publish(draft.id, creator, confirmed = false)
        val qId = questions.findByQuestionnaireId(draft.id).first().id

        val incomplete = incompleteUser("cantvote@test.local")
        assertThatThrownBy {
            responseController.submit(
                AppUserDetails(incomplete),
                draft.id,
                SubmitResponsesRequest(answers = listOf(QuestionAnswerInput(qId, "Yes"))),
            )
        }
            .isInstanceOf(ResponseStatusException::class.java)
            .extracting("statusCode").isEqualTo(HttpStatus.BAD_REQUEST)

        // Once completed, the same user can submit.
        auth.completeProfile(
            AppUserDetails(incomplete),
            CompleteProfileRequest(phone = "+15550009004", zipcode = "90001"),
        )
        val completed = users.findById(incomplete.id).get()
        val result = responseController.submit(
            AppUserDetails(completed),
            draft.id,
            SubmitResponsesRequest(answers = listOf(QuestionAnswerInput(qId, "Yes"))),
        )
        assertThat(result.hasResponses).isTrue
    }
}
