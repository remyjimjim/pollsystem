package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.model.Question
import org.kodewerks.pollsystem.model.Questionnaire
import org.kodewerks.pollsystem.model.ScopeLevel
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.Instant

data class QuestionnaireDraftRequest(
    val pollTypeId: Long,
    @field:NotBlank @field:Size(max = 500) val title: String,
    @field:NotBlank val summary: String,
    val closeDate: Instant? = null,
    @field:NotEmpty @field:Valid val questions: List<QuestionInput>,
    // Purview: zip list (ZIP), county/state ids (COUNTY/STATE), or none (NATIONAL).
    val scopeLevel: ScopeLevel = ScopeLevel.ZIP,
    val regionIds: List<Long> = emptyList(),
    val zipcodes: List<String> = emptyList()
)

data class QuestionInput(
    @field:NotBlank @field:Size(max = 1000) val text: String
)

data class QuestionDto(val id: Long, val text: String) {
    companion object {
        fun from(q: Question) = QuestionDto(q.id, q.question)
    }
}

data class QuestionnaireDto(
    val id: Long,
    val pollTypeId: Long,
    val creatorId: Long,
    val title: String,
    val summary: String,
    val status: PollStatus,
    val closeDate: Instant?,
    val createDate: String,
    val submitDate: Instant?,
    val questions: List<QuestionDto>,
    /** Purview scope + selections (for edit prefill) and a display label. */
    val scopeLevel: ScopeLevel,
    val regionIds: List<Long>,
    val regionStateIds: List<Long>,
    val regionLabel: String,
    val zipcodes: List<String>
) {
    companion object {
        fun from(
            q: Questionnaire,
            questions: List<Question>,
            scopeLevel: ScopeLevel,
            regionIds: List<Long>,
            regionStateIds: List<Long>,
            regionLabel: String,
            zipcodes: List<String>
        ) = QuestionnaireDto(
            id = q.id,
            pollTypeId = q.pollType.id,
            creatorId = q.creator.id,
            title = q.title,
            summary = q.summary,
            status = q.status,
            closeDate = q.closeDate,
            createDate = q.createDate.toString(),
            submitDate = q.submitDate,
            questions = questions.map(QuestionDto::from),
            scopeLevel = scopeLevel,
            regionIds = regionIds,
            regionStateIds = regionStateIds,
            regionLabel = regionLabel,
            zipcodes = zipcodes
        )
    }
}

data class PublishWarning(val closeDate: Instant, val message: String)
