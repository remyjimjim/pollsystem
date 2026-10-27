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
import org.springframework.web.bind.annotation.PathVariable
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
    val details: String?,
    /** An earlier snapshot exists to compare with: the Diff link. */
    val hasDiff: Boolean = false
)

/** The Diff page: the poll as it was at the previous snapshot and as of this entry. */
data class PollHistoryDiff(
    val id: Long,
    val type: String,
    val pollId: Long,
    val pollTitle: String,
    val event: String,
    val at: Instant,
    val before: PollHistoryService.Snapshot,
    val now: PollHistoryService.Snapshot
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
    private val adminPolls: AdminPollsController,
    private val history: PollHistoryService
) {
    private data class PollRef(val title: String, val creatorId: Long)

    private fun pollRef(kind: PollKind, pollId: Long): PollRef? = when (kind) {
        PollKind.QUESTIONNAIRE -> questionnaires.findById(pollId).orElse(null)?.let { PollRef(it.title, it.creator.id) }
        PollKind.ELECTION -> elections.findById(pollId).orElse(null)?.let { PollRef(it.title, it.creator.id) }
        PollKind.BALLOT_MEASURE -> ballotMeasures.findById(pollId).orElse(null)?.let { PollRef(it.title, it.creator.id) }
    }

    private fun canSee(principal: AppUserDetails, kind: PollKind, pollId: Long, poll: PollRef): Boolean {
        val me = principal.user
        return when {
            me.access == AccessLevel.SUPER -> true
            poll.creatorId == me.id -> true
            me.access == AccessLevel.ADMIN -> adminPolls.canSee(principal, kind, pollId)
            else -> false
        }
    }

    @GetMapping("/{eventId}/diff")
    @Transactional(readOnly = true)
    fun diff(@AuthenticationPrincipal principal: AppUserDetails, @PathVariable eventId: Long): PollHistoryDiff {
        if (principal.user.access < AccessLevel.CREATOR) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Creators and above only")
        val e = events.findById(eventId).orElse(null) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val poll = pollRef(e.pollType, e.pollId)
        if (poll == null || !canSee(principal, e.pollType, e.pollId, poll)) throw ResponseStatusException(HttpStatus.NOT_FOUND)
        val (before, now) = history.diff(e) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Nothing to compare")
        return PollHistoryDiff(e.id, slug(e.pollType), e.pollId, poll.title, e.event.name, e.at, before, now)
    }

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
        fun pollOf(kind: PollKind, pollId: Long): PollRef? = polls.getOrPut(kind to pollId) { pollRef(kind, pollId) }
        val visible = HashMap<Pair<PollKind, Long>, Boolean>()
        fun canSee(e: PollEvent): Boolean = visible.getOrPut(e.pollType to e.pollId) {
            val poll = pollOf(e.pollType, e.pollId) ?: return@getOrPut false
            canSee(principal, e.pollType, e.pollId, poll)
        }
        val source = if (onlyKind != null && id != null) events.findByPollTypeAndPollIdOrderByIdDesc(onlyKind, id)
                     else events.findAll(Sort.by(Sort.Direction.DESC, "at", "id"))
        // An entry has a Diff when it has a snapshot and an earlier entry of its poll does too.
        val withDiff = source.groupBy { it.pollType to it.pollId }.values.flatMap { evs ->
            evs.sortedBy { it.id }.filter { it.snapshot != null }.drop(1).map { it.id }
        }.toSet()
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
                    details = e.details,
                    hasDiff = e.id in withDiff
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
