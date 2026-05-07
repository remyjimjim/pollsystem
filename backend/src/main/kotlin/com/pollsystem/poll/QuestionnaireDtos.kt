package com.pollsystem.poll

import com.pollsystem.model.PollStatus
import com.pollsystem.model.Question
import com.pollsystem.model.Questionnaire
import com.pollsystem.model.QuestionnaireDomain
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
    @field:NotEmpty val zipcodes: List<String>
)

data class QuestionInput(
    @field:NotBlank @field:Size(max = 1000) val text: String
)

data class QuestionDto(val id: Long, val text: String) {
    companion object {
        fun from(q: Question) = QuestionDto(q.id, q.question)
    }
}

data class DomainDto(val zipcode: String, val countyId: Long, val stateId: Long) {
    companion object {
        fun from(d: QuestionnaireDomain) =
            DomainDto(d.zipcode, d.county.id, d.state.id)
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
    val domains: List<DomainDto>
) {
    companion object {
        fun from(
            q: Questionnaire,
            questions: List<Question>,
            domains: List<QuestionnaireDomain>
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
            domains = domains.map(DomainDto::from)
        )
    }
}

data class PublishWarning(val closeDate: Instant, val message: String)
