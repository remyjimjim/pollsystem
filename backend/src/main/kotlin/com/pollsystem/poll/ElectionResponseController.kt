package com.pollsystem.poll

import com.pollsystem.model.CandidateResponse
import com.pollsystem.model.PollStatus
import com.pollsystem.repository.CandidateRepository
import com.pollsystem.repository.CandidateResponseRepository
import com.pollsystem.repository.ElectionRepository
import com.pollsystem.security.AppUserDetails
import jakarta.validation.Valid
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

data class CandidateAnswerInput(
    val candidateId: Long,
    val response: Boolean,
    val comment: String? = null
)

data class SubmitElectionResponsesRequest(
    @field:NotEmpty @field:Valid val answers: List<CandidateAnswerInput>
)

data class MyCandidateResponseDto(
    val candidateId: Long,
    val response: Boolean,
    val comment: String?,
    val dateSubmitted: Instant,
    val lastModified: Instant?
)

data class MyElectionResponsesDto(
    val electionId: Long,
    val hasResponses: Boolean,
    val firstSubmittedAt: Instant?,
    val responses: List<MyCandidateResponseDto>
)

@RestController
@RequestMapping("/api/polls/elections/{id}/responses")
class ElectionResponseController(
    private val elections: ElectionRepository,
    private val candidates: CandidateRepository,
    private val responses: CandidateResponseRepository,
    private val blocks: PollBlockService
) {

    @GetMapping("/me")
    @Transactional(readOnly = true)
    fun mine(
        @AuthenticationPrincipal principal: AppUserDetails,
        @PathVariable id: Long
    ): MyElectionResponsesDto {
        elections.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Election not found")
        }
        val mine = responses.findByElectionIdAndUserId(id, principal.user.id)
        return MyElectionResponsesDto(
            electionId = id,
            hasResponses = mine.isNotEmpty(),
            firstSubmittedAt = mine.minOfOrNull { it.dateSubmitted },
            responses = mine.map {
                MyCandidateResponseDto(
                    candidateId = it.candidate.id,
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
        @Valid @RequestBody body: SubmitElectionResponsesRequest
    ): MyElectionResponsesDto {
        val election = elections.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Election not found")
        }
        if (election.status != PollStatus.PUBLISHED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This poll is not accepting responses")
        }
        val close = election.closeDate
        if (close != null && !close.isAfter(Instant.now())) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This poll is closed")
        }
        if (blocks.isBlocked(com.pollsystem.model.PollKind.ELECTION, listOf(election.zipcode))) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Submissions disabled by admin for this area")
        }

        val candidatesById = candidates.findByElectionId(id).associateBy { it.id }
        val unknown = body.answers.map { it.candidateId }.filterNot { it in candidatesById }
        if (unknown.isNotEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown candidateIds: $unknown")
        }
        val missing = candidatesById.keys - body.answers.map { it.candidateId }.toSet()
        if (missing.isNotEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing answers for candidates: $missing")
        }

        val now = Instant.now()
        val existingByCandidateId = responses
            .findByElectionIdAndUserId(id, principal.user.id)
            .associateBy { it.candidate.id }

        val toSave = body.answers.map { ans ->
            val candidate = candidatesById.getValue(ans.candidateId)
            val existing = existingByCandidateId[ans.candidateId]
            if (existing != null) {
                existing.copy(
                    response = ans.response,
                    comment = ans.comment?.trim(),
                    lastModified = now
                )
            } else {
                CandidateResponse(
                    user = principal.user,
                    candidate = candidate,
                    response = ans.response,
                    comment = ans.comment?.trim(),
                    dateSubmitted = now
                )
            }
        }
        responses.saveAll(toSave)

        return mine(principal, id)
    }
}
