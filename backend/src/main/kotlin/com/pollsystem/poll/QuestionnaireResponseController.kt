package com.pollsystem.poll

import com.pollsystem.model.PollKind
import com.pollsystem.model.PollStatus
import com.pollsystem.model.QuestionResponse
import com.pollsystem.repository.QuestionRepository
import com.pollsystem.repository.QuestionResponseRepository
import com.pollsystem.repository.QuestionnaireDomainRepository
import com.pollsystem.repository.QuestionnaireRepository
import com.pollsystem.security.AppUserDetails
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

data class QuestionAnswerInput(
    val questionId: Long,
    @field:NotBlank val response: String,
    val comment: String? = null
)

data class SubmitResponsesRequest(
    @field:NotEmpty @field:Valid val answers: List<QuestionAnswerInput>
)

data class MyResponseDto(
    val questionId: Long,
    val response: String,
    val comment: String?,
    val dateSubmitted: Instant,
    val lastModified: Instant?
)

data class MyResponsesDto(
    val questionnaireId: Long,
    val hasResponses: Boolean,
    val firstSubmittedAt: Instant?,
    val responses: List<MyResponseDto>
)

@RestController
@RequestMapping("/api/polls/questionnaires/{id}/responses")
class QuestionnaireResponseController(
    private val questionnaires: QuestionnaireRepository,
    private val questions: QuestionRepository,
    private val responses: QuestionResponseRepository,
    private val domains: QuestionnaireDomainRepository,
    private val blocks: PollBlockService
) {

    @GetMapping("/me")
    @Transactional(readOnly = true)
    fun mine(
        @AuthenticationPrincipal principal: AppUserDetails,
        @PathVariable id: Long
    ): MyResponsesDto {
        questionnaires.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Questionnaire not found")
        }
        val mine = responses.findByQuestionnaireIdAndUserId(id, principal.user.id)
        return MyResponsesDto(
            questionnaireId = id,
            hasResponses = mine.isNotEmpty(),
            firstSubmittedAt = mine.minOfOrNull { it.dateSubmitted },
            responses = mine.map {
                MyResponseDto(
                    questionId = it.question.id,
                    response = it.response,
                    comment = it.comment,
                    dateSubmitted = it.dateSubmitted,
                    lastModified = it.lastModified
                )
            }
        )
    }

    @PostMapping
    @Transactional
    fun submit(
        @AuthenticationPrincipal principal: AppUserDetails,
        @PathVariable id: Long,
        @Valid @RequestBody body: SubmitResponsesRequest
    ): MyResponsesDto {
        val q = questionnaires.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Questionnaire not found")
        }
        if (q.status != PollStatus.PUBLISHED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This poll is not accepting responses")
        }
        val close = q.closeDate
        if (close != null && !close.isAfter(Instant.now())) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This poll is closed")
        }
        if (blocks.isBlocked(PollKind.QUESTIONNAIRE, id)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Submissions disabled by admin for this area")
        }

        val pollQuestions = questions.findByQuestionnaireId(id).associateBy { it.id }
        val unknown = body.answers.map { it.questionId }.filterNot { it in pollQuestions }
        if (unknown.isNotEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown questionIds: $unknown")
        }
        val missing = pollQuestions.keys - body.answers.map { it.questionId }.toSet()
        if (missing.isNotEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing answers for questions: $missing")
        }

        val now = Instant.now()
        val existingByQuestionId = responses
            .findByQuestionnaireIdAndUserId(id, principal.user.id)
            .associateBy { it.question.id }

        val toSave = body.answers.map { ans ->
            val question = pollQuestions.getValue(ans.questionId)
            val existing = existingByQuestionId[ans.questionId]
            if (existing != null) {
                existing.copy(
                    response = ans.response.trim(),
                    comment = ans.comment?.trim(),
                    lastModified = now
                )
            } else {
                QuestionResponse(
                    question = question,
                    response = ans.response.trim(),
                    user = principal.user,
                    comment = ans.comment?.trim(),
                    dateSubmitted = now
                )
            }
        }
        responses.saveAll(toSave)

        return mine(principal, id)
    }
}
