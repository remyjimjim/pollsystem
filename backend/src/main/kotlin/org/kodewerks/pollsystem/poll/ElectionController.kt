package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.Candidate
import org.kodewerks.pollsystem.model.Election
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.security.AppUserDetails
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate

data class CandidateInput(
    @field:NotBlank @field:Size(max = 255) val name: String,
    @field:NotBlank @field:Size(max = 255) val affiliation: String,
    @field:NotBlank @field:Size(max = 255) val officeName: String,
    val officeDesc: String? = null
)

data class CandidateDto(
    val id: Long,
    val name: String,
    val affiliation: String,
    val officeId: Long,
    val officeName: String
) {
    companion object {
        fun from(c: Candidate) = CandidateDto(
            c.id, c.name, c.affiliation, c.office.id, c.office.name
        )
    }
}

data class ElectionDraftRequest(
    val pollTypeId: Long,
    @field:NotBlank @field:Size(max = 500) val title: String,
    val date: LocalDate,
    @field:NotBlank @field:Pattern(regexp = "^[0-9]{5}$") val zipcode: String,
    val closeDate: Instant? = null,
    @field:Valid val candidates: List<CandidateInput> = emptyList()
)

data class ElectionDto(
    val id: Long,
    val pollTypeId: Long,
    val creatorId: Long,
    val title: String,
    val date: LocalDate,
    val zipcode: String,
    val status: PollStatus,
    val closeDate: Instant?,
    val dateSubmitted: Instant,
    val candidates: List<CandidateDto>,
    /** Rendering hint pulled from the parent PollType's template_json; null
     *  if the template has no hint, in which case the frontend falls back
     *  to the legacy per-candidate Yes/No ballot. */
    val candidatesWidget: String?,
    /** Field name to group candidates by before rendering (e.g. "officeName"). */
    val candidatesGroupBy: String?
) {
    companion object {
        fun from(
            e: Election,
            candidates: List<Candidate>,
            candidatesWidget: String?,
            candidatesGroupBy: String?
        ) = ElectionDto(
            id = e.id,
            pollTypeId = e.pollType.id,
            creatorId = e.creator.id,
            title = e.title,
            date = e.date,
            zipcode = e.zipcode,
            status = e.status,
            closeDate = e.closeDate,
            dateSubmitted = e.dateSubmitted,
            candidates = candidates.map(CandidateDto::from),
            candidatesWidget = candidatesWidget,
            candidatesGroupBy = candidatesGroupBy
        )
    }
}

@RestController
@RequestMapping("/api/polls/elections")
class ElectionController(private val service: ElectionService) {

    @PostMapping
    fun saveDraft(
        @AuthenticationPrincipal principal: AppUserDetails,
        @Valid @RequestBody body: ElectionDraftRequest
    ): ResponseEntity<ElectionDto> {
        val saved = service.saveDraft(principal.user, body)
        return ResponseEntity.status(HttpStatus.CREATED).body(service.toDto(saved))
    }

    @PutMapping("/{id}")
    fun update(
        @AuthenticationPrincipal principal: AppUserDetails,
        @PathVariable id: Long,
        @Valid @RequestBody body: ElectionDraftRequest
    ): ElectionDto = service.toDto(service.update(id, principal.user, body))

    @PostMapping("/{id}/publish")
    fun publish(
        @AuthenticationPrincipal principal: AppUserDetails,
        @PathVariable id: Long,
        @RequestParam(defaultValue = "false") confirmed: Boolean
    ): ElectionDto = service.toDto(service.publish(id, principal.user, confirmed))

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): ElectionDto = service.toDto(service.get(id))
}
