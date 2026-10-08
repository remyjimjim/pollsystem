package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.repository.BallotMeasureRepository
import org.kodewerks.pollsystem.repository.ElectionRepository
import org.kodewerks.pollsystem.repository.QuestionnaireRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

data class CreatorPollSummary(
    val id: Long,
    val type: String,
    val title: String,
    val status: PollStatus,
    val closeDate: Instant?,
    val createdAt: Instant,
    /** People who have answered (0 for drafts). */
    val respondents: Int = 0,
    /** How many of them live inside the poll's purview; null = withheld (small group). */
    val inArea: Int? = 0
)

@RestController
@RequestMapping("/api/creator/polls")
class CreatorPollsController(
    private val questionnaires: QuestionnaireRepository,
    private val elections: ElectionRepository,
    private val ballotMeasures: BallotMeasureRepository,
    private val participation: PollParticipationService
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: AppUserDetails,
        @RequestParam(name = "showArchived", required = false, defaultValue = "false") showArchived: Boolean
    ): List<CreatorPollSummary> {
        val id = principal.user.id
        val q = questionnaires.findByCreatorId(id).map {
            CreatorPollSummary(
                it.id, "Questionnaire", it.title, it.status,
                it.closeDate, it.submitDate ?: Instant.now()
            )
        }
        val e = elections.findByCreatorId(id).map {
            CreatorPollSummary(
                it.id, "Election", it.title, it.status,
                it.closeDate, it.dateSubmitted
            )
        }
        // BallotMeasure repo lacks a findByCreatorId — derive from elections for now
        val b = ballotMeasures.findAll()
            .filter { it.creator.id == id }
            .map {
                CreatorPollSummary(
                    it.id, "BallotMeasure", it.title, it.status,
                    it.closeDate, it.dateCreated
                )
            }
        val all = (q + e + b).sortedByDescending { it.createdAt }
        val listed = if (showArchived) all else all.filterNot { it.status == PollStatus.ARCHIVED }
        // Drafts have no responses; count the rest (a creator has few polls).
        return listed.map { p ->
            if (p.status == PollStatus.DRAFT) p
            else participation.of(kindOf(p.type), p.id).let { p.copy(respondents = it.respondents, inArea = it.inArea) }
        }
    }

    /**
     * Soft-deletes the creator's own poll by setting status=ARCHIVED. Archived
     * polls disappear from the dashboard listing and from search/voting (every
     * voter-facing query filters on PUBLISHED), but rows and any cast responses
     * are preserved so the deletion is recoverable by a Super if needed.
     */
    @DeleteMapping("/{type}/{id}")
    @Transactional
    fun delete(
        @AuthenticationPrincipal principal: AppUserDetails,
        @PathVariable type: String,
        @PathVariable id: Long
    ) {
        val callerId = principal.user.id
        when (type) {
            "questionnaire" -> {
                val q = questionnaires.findById(id).orElseThrow { notFound() }
                if (q.creator.id != callerId) throw forbidden()
                questionnaires.save(q.copy(status = PollStatus.ARCHIVED, creatorEditedAt = Instant.now()))
            }
            "election" -> {
                val e = elections.findById(id).orElseThrow { notFound() }
                if (e.creator.id != callerId) throw forbidden()
                elections.save(e.copy(status = PollStatus.ARCHIVED, creatorEditedAt = Instant.now()))
            }
            "ballot-measure" -> {
                val bm = ballotMeasures.findById(id).orElseThrow { notFound() }
                if (bm.creator.id != callerId) throw forbidden()
                ballotMeasures.save(bm.copy(status = PollStatus.ARCHIVED, creatorEditedAt = Instant.now()))
            }
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown poll type: $type")
        }
    }

    /**
     * Restores an archived poll back to DRAFT so the creator can review and
     * re-publish. We don't remember the pre-archive status, so DRAFT is the
     * safest landing — the creator hits Publish again if they want it live.
     */
    @PostMapping("/{type}/{id}/restore")
    @Transactional
    fun restore(
        @AuthenticationPrincipal principal: AppUserDetails,
        @PathVariable type: String,
        @PathVariable id: Long
    ) {
        val callerId = principal.user.id
        when (type) {
            "questionnaire" -> {
                val q = questionnaires.findById(id).orElseThrow { notFound() }
                if (q.creator.id != callerId) throw forbidden()
                if (q.status != PollStatus.ARCHIVED) return
                questionnaires.save(q.copy(status = PollStatus.DRAFT, creatorEditedAt = Instant.now()))
            }
            "election" -> {
                val e = elections.findById(id).orElseThrow { notFound() }
                if (e.creator.id != callerId) throw forbidden()
                if (e.status != PollStatus.ARCHIVED) return
                elections.save(e.copy(status = PollStatus.DRAFT, creatorEditedAt = Instant.now()))
            }
            "ballot-measure" -> {
                val bm = ballotMeasures.findById(id).orElseThrow { notFound() }
                if (bm.creator.id != callerId) throw forbidden()
                if (bm.status != PollStatus.ARCHIVED) return
                ballotMeasures.save(bm.copy(status = PollStatus.DRAFT, creatorEditedAt = Instant.now()))
            }
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown poll type: $type")
        }
    }

    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND, "Poll not found")
    private fun forbidden() = ResponseStatusException(HttpStatus.FORBIDDEN, "Not your poll")

    private fun kindOf(type: String): PollKind = when (type) {
        "Questionnaire" -> PollKind.QUESTIONNAIRE
        "Election" -> PollKind.ELECTION
        else -> PollKind.BALLOT_MEASURE
    }
}
