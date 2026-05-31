package com.pollsystem.poll

import com.pollsystem.model.PollKind
import com.pollsystem.repository.QuestionRepository
import com.pollsystem.repository.QuestionResponseRepository
import com.pollsystem.repository.QuestionnaireDomainRepository
import com.pollsystem.repository.QuestionnaireRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class QuestionResultDto(
    val questionId: Long,
    val text: String,
    val totalResponses: Int,
    val byAnswer: Map<String, Int>
)

data class QuestionnaireResultsDto(
    val questionnaireId: Long,
    val title: String,
    val totalRespondents: Int,
    val perQuestion: List<QuestionResultDto>,
    val filterApplied: Map<String, String>?,
    val suppressed: Boolean,
    val suppressionMessage: String?
)

@RestController
@RequestMapping("/api/polls/questionnaires/{id}/results")
class QuestionnaireResultsController(
    private val questionnaires: QuestionnaireRepository,
    private val questions: QuestionRepository,
    private val responses: QuestionResponseRepository,
    private val domains: QuestionnaireDomainRepository,
    private val blocks: PollBlockService,
    @Value("\${app.results.k-anonymity-threshold:10}") private val kThreshold: Int
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun get(
        @PathVariable id: Long,
        @RequestParam(required = false) zipcode: String?
    ): QuestionnaireResultsDto {
        val q = questionnaires.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Questionnaire not found")
        }
        val qZips = domains.findByQuestionnaireId(id).map { it.zipcode }
        if (blocks.isBlocked(PollKind.QUESTIONNAIRE, qZips)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Questionnaire not found")
        }

        val pollQuestions = questions.findByQuestionnaireId(id)
        val all = responses.findByQuestionnaireId(id)
        val filtered = if (zipcode != null) {
            all.filter { it.user.zipcode == zipcode }
        } else {
            all
        }

        val respondents = filtered.map { it.user.id }.distinct().size
        val filterMap = zipcode?.let { mapOf("zipcode" to it) }

        if (zipcode != null && respondents < kThreshold) {
            return QuestionnaireResultsDto(
                questionnaireId = id,
                title = q.title,
                totalRespondents = 0,
                perQuestion = emptyList(),
                filterApplied = filterMap,
                suppressed = true,
                suppressionMessage = "Not enough responses in this group to display (privacy protection)"
            )
        }

        val byQuestion = filtered.groupBy { it.question.id }
        val perQuestion = pollQuestions.map { question ->
            val rows = byQuestion[question.id].orEmpty()
            val byAnswer = rows
                .groupingBy { it.response.trim().lowercase() }
                .eachCount()
            QuestionResultDto(
                questionId = question.id,
                text = question.question,
                totalResponses = rows.size,
                byAnswer = byAnswer
            )
        }

        return QuestionnaireResultsDto(
            questionnaireId = id,
            title = q.title,
            totalRespondents = respondents,
            perQuestion = perQuestion,
            filterApplied = filterMap,
            suppressed = false,
            suppressionMessage = null
        )
    }
}
