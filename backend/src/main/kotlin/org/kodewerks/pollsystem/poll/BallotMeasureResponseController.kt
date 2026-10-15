package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.BallotResponse
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.repository.BallotMeasureRepository
import org.kodewerks.pollsystem.repository.BallotResponseRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
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

data class SubmitBallotResponseRequest(
    // Nullable so Jackson lets through `null` and missing fields, then @NotNull
    // catches them in Bean Validation. With non-nullable Boolean, Kotlin/Jackson
    // defaults missing fields to false and @NotNull never fires.
    @field:NotNull val response: Boolean?,
    val comment: String? = null,
    /** "Make comment public (and anonymous)": stored only if the comment is clean. */
    val commentPublic: Boolean = false
)

data class MyBallotResponseDto(
    val measureId: Long,
    val hasResponse: Boolean,
    val response: Boolean?,
    val comment: String?,
    val commentPublic: Boolean = false,
    val dateSubmitted: Instant?,
    val lastModified: Instant?
)

@RestController
@RequestMapping("/api/polls/ballot-measures/{id}/responses")
class BallotMeasureResponseController(
    private val measures: BallotMeasureRepository,
    private val responses: BallotResponseRepository,
    private val blocks: PollBlockService,
    private val snapshots: ResultSnapshotService,
    private val badWords: org.kodewerks.pollsystem.moderation.BadWords
) {

    @GetMapping("/me")
    @Transactional(readOnly = true)
    fun mine(
        @AuthenticationPrincipal principal: AppUserDetails,
        @PathVariable id: Long
    ): MyBallotResponseDto {
        measures.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Ballot measure not found")
        }
        val existing = responses.findByUserIdAndMeasureId(principal.user.id, id)
        return existing
            ?.let {
                MyBallotResponseDto(
                    measureId = id,
                    hasResponse = true,
                    response = it.response,
                    comment = it.comment,
                    commentPublic = it.commentPublic,
                    dateSubmitted = it.dateSubmitted,
                    lastModified = it.lastModified
                )
            }
            ?: MyBallotResponseDto(
                measureId = id,
                hasResponse = false,
                response = null,
                comment = null,
                dateSubmitted = null,
                lastModified = null
            )
    }

    @PostMapping
    @Transactional
    fun submit(
        @AuthenticationPrincipal principal: AppUserDetails,
        @PathVariable id: Long,
        @Valid @RequestBody body: SubmitBallotResponseRequest
    ): MyBallotResponseDto {
        requireParticipation(principal)
        val measure = measures.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Ballot measure not found")
        }
        if (measure.status != PollStatus.PUBLISHED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This poll is not accepting responses")
        }
        val close = measure.closeDate
        if (close != null && !close.isAfter(Instant.now())) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "This poll is closed")
        }
        if (blocks.isBlockedFor(org.kodewerks.pollsystem.model.PollKind.BALLOT_MEASURE, measure.id, principal.user.zipcode)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Submissions disabled by admin for this area")
        }

        val now = Instant.now()
        val existing = responses.findByUserIdAndMeasureId(principal.user.id, id)
        val saved = if (existing != null) {
            responses.save(
                existing.copy(
                    response = body.response!!,
                    comment = body.comment?.trim(),
                    commentPublic = badWords.publishable(body.comment, body.commentPublic),
                    lastModified = now
                )
            )
        } else {
            responses.save(
                BallotResponse(
                    measure = measure,
                    user = principal.user,
                    response = body.response!!,
                    comment = body.comment?.trim(),
                    commentPublic = badWords.publishable(body.comment, body.commentPublic),
                    dateSubmitted = now
                )
            )
        }
        // Public results move only in steps of k respondents (small-poll privacy).
        snapshots.refresh(PollKind.BALLOT_MEASURE, id)

        return MyBallotResponseDto(
            measureId = id,
            hasResponse = true,
            response = saved.response,
            comment = saved.comment,
            commentPublic = saved.commentPublic,
            dateSubmitted = saved.dateSubmitted,
            lastModified = saved.lastModified
        )
    }
}
