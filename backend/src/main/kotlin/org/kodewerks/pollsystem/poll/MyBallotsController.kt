package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.repository.BallotResponseRepository
import org.kodewerks.pollsystem.repository.CandidateResponseRepository
import org.kodewerks.pollsystem.repository.QuestionResponseRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/** One poll the signed-in member answered, for "View past ballots". */
data class MyBallot(
    /** questionnaire | election */
    val type: String,
    val id: Long,
    val title: String,
    /** When they last voted on it (any of its races or measures). */
    val votedAt: Instant,
    /** Still open for changing the vote. */
    val open: Boolean,
    val closeDate: Instant? = null,
    /** Switched on where the member lives (not switched off there by an admin). */
    val enabled: Boolean = true
)

/**
 * "View past ballots" (2026-10-28): every poll the signed-in member has
 * answered, newest vote first. A midterm district ballot appears once: its
 * statewide races (included elections) and its ballot measures fold into it,
 * since they're voted on the same page. A measure whose election the member
 * didn't vote on lists that election, where the measure is reviewed.
 */
@RestController
@RequestMapping("/api/my/ballots")
class MyBallotsController(
    private val questionResponses: QuestionResponseRepository,
    private val candidateResponses: CandidateResponseRepository,
    private val ballotResponses: BallotResponseRepository,
    private val includes: ElectionIncludes,
    private val blocks: PollBlockService
) {
    @GetMapping
    @Transactional(readOnly = true)
    fun mine(@AuthenticationPrincipal principal: AppUserDetails): List<MyBallot> {
        val me = principal.user.id
        val myZip = principal.user.zipcode
        fun enabled(kind: org.kodewerks.pollsystem.model.PollKind, id: Long) = !blocks.isBlockedFor(kind, id, myZip)
        val now = Instant.now()
        fun isOpen(status: PollStatus, closeDate: Instant?) =
            status == PollStatus.PUBLISHED && (closeDate == null || closeDate.isAfter(now))

        val rows = mutableListOf<MyBallot>()
        questionResponses.findByUserId(me).groupBy { it.question.questionnaire.id }.values.forEach { group ->
            val q = group.first().question.questionnaire
            rows += MyBallot("questionnaire", q.id, q.title, group.maxOf { it.dateSubmitted }, isOpen(q.status, q.closeDate),
                q.closeDate, enabled(org.kodewerks.pollsystem.model.PollKind.QUESTIONNAIRE, q.id))
        }

        // Elections: votes on candidates, plus measures (voted with their election).
        class Ballot(val election: org.kodewerks.pollsystem.model.Election, var votedAt: Instant)
        val byElection = LinkedHashMap<Long, Ballot>()
        fun add(e: org.kodewerks.pollsystem.model.Election, at: Instant) {
            val b = byElection.getOrPut(e.id) { Ballot(e, at) }
            if (at.isAfter(b.votedAt)) b.votedAt = at
        }
        candidateResponses.findByUserId(me).forEach { add(it.candidate.election, it.dateSubmitted) }
        ballotResponses.findByUserId(me).forEach { add(it.measure.election, it.dateSubmitted) }

        // Fold included (statewide) elections into a ballot that includes them.
        val includedBy = includes.all()
        val folded = mutableSetOf<Long>()
        for ((ballotId, ballot) in byElection) {
            for (inc in includedBy[ballotId].orEmpty()) {
                val part = byElection[inc] ?: continue
                if (part.votedAt.isAfter(ballot.votedAt)) ballot.votedAt = part.votedAt
                folded += inc
            }
        }
        byElection.values.filter { it.election.id !in folded }.forEach { b ->
            rows += MyBallot("election", b.election.id, b.election.title, b.votedAt, isOpen(b.election.status, b.election.closeDate),
                b.election.closeDate, enabled(org.kodewerks.pollsystem.model.PollKind.ELECTION, b.election.id))
        }
        return rows.sortedByDescending { it.votedAt }
    }
}
