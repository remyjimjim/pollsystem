package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.BallotMeasure
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.security.AppUserDetails
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
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

data class BallotMeasureDraftRequest(
    val pollTypeId: Long,
    val electionId: Long,
    @field:NotBlank @field:Size(max = 500) val title: String,
    @field:NotBlank val summary: String,
    val effectiveDate: LocalDate,
    val closeDate: Instant? = null
)

data class BallotMeasureDto(
    val id: Long,
    val pollTypeId: Long,
    val creatorId: Long,
    val electionId: Long,
    val electionTitle: String,
    val title: String,
    val summary: String,
    val effectiveDate: LocalDate,
    val zipcode: String,
    val status: PollStatus,
    val closeDate: Instant?,
    val dateCreated: Instant,
    val lastUpdated: Instant
) {
    companion object {
        fun from(b: BallotMeasure) = BallotMeasureDto(
            id = b.id,
            pollTypeId = b.pollType.id,
            creatorId = b.creator.id,
            electionId = b.election.id,
            electionTitle = b.election.title,
            title = b.title,
            summary = b.summary,
            effectiveDate = b.effectiveDate,
            zipcode = b.election.zipcode,
            status = b.status,
            closeDate = b.closeDate,
            dateCreated = b.dateCreated,
            lastUpdated = b.lastUpdated
        )
    }
}

@RestController
@RequestMapping("/api/polls/ballot-measures")
class BallotMeasureController(private val service: BallotMeasureService) {

    @PostMapping
    fun saveDraft(
        @AuthenticationPrincipal principal: AppUserDetails,
        @Valid @RequestBody body: BallotMeasureDraftRequest
    ): ResponseEntity<BallotMeasureDto> {
        val saved = service.saveDraft(principal.user, body)
        return ResponseEntity.status(HttpStatus.CREATED).body(BallotMeasureDto.from(saved))
    }

    @PutMapping("/{id}")
    fun update(
        @AuthenticationPrincipal principal: AppUserDetails,
        @PathVariable id: Long,
        @Valid @RequestBody body: BallotMeasureDraftRequest
    ): BallotMeasureDto = BallotMeasureDto.from(service.update(id, principal.user, body))

    @PostMapping("/{id}/publish")
    fun publish(
        @AuthenticationPrincipal principal: AppUserDetails,
        @PathVariable id: Long,
        @RequestParam(defaultValue = "false") confirmed: Boolean
    ): BallotMeasureDto = BallotMeasureDto.from(service.publish(id, principal.user, confirmed))

    @GetMapping("/{id}")
    fun get(@PathVariable id: Long): BallotMeasureDto = BallotMeasureDto.from(service.get(id))
}
