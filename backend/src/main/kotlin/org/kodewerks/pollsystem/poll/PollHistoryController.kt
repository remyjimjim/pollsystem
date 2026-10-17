package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.adminpolls.AdminPollsController
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.PollEvent
import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.repository.BallotMeasureRepository
import org.kodewerks.pollsystem.repository.ElectionRepository
import org.kodewerks.pollsystem.repository.PollEventRepository
import org.kodewerks.pollsystem.repository.QuestionnaireRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/** One history entry as the History page shows it. */
data class PollHistoryRow(
    val id: Long,
    /** questionnaire | election | ballot-measure */
    val type: String,
    val pollId: Long,
    val pollTitle: String,
    val event: String,
    val at: Instant,
    /** "you", "automatic", or the actor's role (ADMIN / SUPER / CREATOR). */
    val by: String,
    /** The actor's email: admins and supers only (null for creators, and for automatic). */
    val byEmail: String?,
    val details: String?
)

/**
 * The History page (decided 2026-10-17): newest first, optionally one poll.
 * Creators see their own polls; admins also the polls in their area (the
 * Manage Polls rule); supers everything. Members get 403.
 */
@RestController
@RequestMapping("/api/poll-history")
class PollHistoryController(
    private val events: PollEventRepository,
    private val questionnaires: QuestionnaireRepository,
    private val elections: ElectionRepository,
    private val ballotMeasures: BallotMeasureRepository,
    private val adminPolls: AdminPollsController
) {
    private data class PollRef(val title: String, val creatorId: Long)

    @GetMapping
    @Transactional(readOnly = true)
    fun list(
        @AuthenticationPrincipal principal: AppUserDetails,
        @RequestParam(required = false) type: String? = null,
        @RequestParam(required = false) id: Long? = null,
        @RequestParam(defaultValue = "1000") limit: Int = 1000
    ): List<PollHistoryRow> {
        val me = principal.user
        if (me.access < AccessLevel.CREATOR) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Creators and above only")
        val onlyKind = type?.let { kindOf(it) }

        val polls = HashMap<Pair<PollKind, Long>, PollRef?>()
        fun pollOf(kind: PollKind, pollId: Long): PollRef? = polls.getOrPut(kind to pollId) {
            when (kind) {
                PollKind.QUESTIONNAIRE -> questionnaires.findById(pollId).orElse(null)?.let { PollRef(it.title, it.creator.id) }
                PollKind.ELECTION -> elections.findById(pollId).orElse(null)?.let { PollRef(it.title, it.creator.id) }
                PollKind.BALLOT_MEASURE -> ballotMeasures.findById(pollId).orElse(null)?.let { PollRef(it.title, it.creator.id) }
            }
        }
        val visible = HashMap<Pair<PollKind, Long>, Boolean>()
        fun canSee(e: PollEvent): Boolean = visible.getOrPut(e.pollType to e.pollId) {
            val poll = pollOf(e.pollType, e.pollId) ?: return@getOrPut false
            when {
                me.access == AccessLevel.SUPER -> true
                poll.creatorId == me.id -> true
                me.access == AccessLevel.ADMIN -> adminPolls.canSee(principal, e.pollType, e.pollId)
                else -> false
            }
        }
        val source = if (onlyKind != null && id != null) events.findByPollTypeAndPollIdOrderByIdDesc(onlyKind, id)
                     else events.findAll(Sort.by(Sort.Direction.DESC, "at", "id"))
        val staff = me.access >= AccessLevel.ADMIN
        return source.asSequence()
            .filter(::canSee)
            .take(limit.coerceIn(1, 5000))
            .map { e ->
                val actor = e.actor
                PollHistoryRow(
                    id = e.id,
                    type = slug(e.pollType),
                    pollId = e.pollId,
                    pollTitle = pollOf(e.pollType, e.pollId)!!.title,
                    event = e.event.name,
                    at = e.at,
                    by = when {
                        actor == null -> "automatic"
                        actor.id == me.id -> "you"
                        else -> actor.access.name
                    },
                    byEmail = if (staff && actor != null) actor.email else null,
                    details = e.details
                )
            }.toList()
    }

    private fun kindOf(type: String) = when (type) {
        "questionnaire" -> PollKind.QUESTIONNAIRE
        "election" -> PollKind.ELECTION
        "ballot-measure" -> PollKind.BALLOT_MEASURE
        else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown poll type: $type")
    }

    private fun slug(kind: PollKind) = when (kind) {
        PollKind.QUESTIONNAIRE -> "questionnaire"
        PollKind.ELECTION -> "election"
        PollKind.BALLOT_MEASURE -> "ballot-measure"
    }
}
