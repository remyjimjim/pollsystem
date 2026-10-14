package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.repository.QuestionRepository
import org.kodewerks.pollsystem.repository.QuestionnaireRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
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
    /** Raw counts: supers only (null for everyone else). */
    val totalResponses: Int?,
    val byAnswer: Map<String, Int>?,
    /** Rounded percentage per answer (lowercased), for everyone. */
    val byAnswerPct: Map<String, Int>
)

data class QuestionnaireResultsDto(
    val questionnaireId: Long,
    val title: String,
    /** Respondents in the shown group: supers only (null for everyone else). */
    val totalRespondents: Int?,
    val perQuestion: List<QuestionResultDto>,
    val filterApplied: Map<String, String>?,
    val suppressed: Boolean,
    val suppressionMessage: String?,
    /** The poll's purview, so the page can say where the poll is for. */
    val purviewLevel: ScopeLevel = ScopeLevel.NATIONAL,
    /** Its places (states, "County (ST)" or zipcodes); empty when nationwide. */
    val purviewAreas: List<String> = emptyList(),
    val visibility: ResultsVisibility
)

@RestController
@RequestMapping("/api/polls/questionnaires/{id}/results")
class QuestionnaireResultsController(
    private val questionnaires: QuestionnaireRepository,
    private val questions: QuestionRepository,
    private val blocks: PollBlockService,
    private val purviews: PollPurviewService,
    private val access: ResultsAccess
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun get(
        @PathVariable id: Long,
        @RequestParam(name = "zipcode", required = false) zipcodes: List<String>? = null,
        @RequestParam(name = "stateId", required = false) stateIds: List<Long>? = null,
        @RequestParam(name = "countyId", required = false) countyIds: List<Long>? = null,
        @RequestParam(required = false, defaultValue = "true") withinPurview: Boolean = true,
        @RequestParam(required = false, defaultValue = "true") outsidePurview: Boolean = true,
        @AuthenticationPrincipal principal: AppUserDetails? = null
    ): QuestionnaireResultsDto {
        val q = questionnaires.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Questionnaire not found")
        }
        if (blocks.isBlockedEverywhere(PollKind.QUESTIONNAIRE, id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Questionnaire not found")
        }
        val purviewRows = purviews.purviewOf(PollKind.QUESTIONNAIRE, id)
        val r = access.prepare(principal, PollKind.QUESTIONNAIRE, id, zipcodes, stateIds, countyIds, withinPurview, outsidePurview)

        val byQuestion = r.answers.groupBy { it.itemId }
        val perQuestion = if (r.answers.isEmpty() && !r.live) emptyList() else questions.findByQuestionnaireId(id).map { question ->
            val rows = byQuestion[question.id].orEmpty()
            val byAnswer = rows.groupingBy { it.response.trim().lowercase() }.eachCount()
            QuestionResultDto(
                questionId = question.id,
                text = question.question,
                totalResponses = if (r.live) rows.size else null,
                byAnswer = if (r.live) byAnswer else null,
                byAnswerPct = byAnswer.mapValues { (_, n) -> pct(n, rows.size) }
            )
        }
        return QuestionnaireResultsDto(
            questionnaireId = id,
            title = q.title,
            totalRespondents = if (r.live) r.respondents else null,
            perQuestion = perQuestion,
            filterApplied = r.filterApplied,
            suppressed = r.suppressed,
            suppressionMessage = if (r.suppressed) SUPPRESSED else null,
            purviewLevel = purviews.scopeLevelOf(purviewRows),
            purviewAreas = purviews.areaNames(purviewRows),
            visibility = r.visibility
        )
    }
}

internal const val SUPPRESSED = "Not enough responses in this group to display (privacy protection)"
