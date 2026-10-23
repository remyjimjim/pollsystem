package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.Election
import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.User
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

/**
 * Who may vote on a ballot (V38), and taking a vote back.
 *
 * - A residents-only election (the midterms) takes votes only from members
 *   whose zipcode is inside its purview; so do its ballot measures.
 * - District ballots that include the same election (V37) are alternatives:
 *   you live in one district, so you vote on one of them. Having voted on
 *   another one's own races, you cancel that vote first.
 */
@Component
class BallotRules(
    private val jdbc: JdbcTemplate,
    private val purviews: PollPurviewService,
    private val includes: ElectionIncludes
) {

    fun requireResident(e: Election, zip: String?) {
        if (e.residentsOnly && !purviews.includesZip(PollKind.ELECTION, e.id, zip)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "You can only vote on midterms from your own state.")
        }
    }

    /** The other ballot (sharing an included election with this one) whose own races the user voted on, if any. */
    fun otherBallotVoted(electionId: Long, userId: Long): OtherBallot? = jdbc.query(
        """
        SELECT DISTINCT e.id, e.title FROM election_includes mine
        JOIN election_includes sib ON sib.included_election_id = mine.included_election_id
                                  AND sib.election_id <> mine.election_id
        JOIN elections e ON e.id = sib.election_id
        JOIN candidates c ON c.election_id = sib.election_id
        JOIN candidate_responses cr ON cr.candidate_id = c.id AND cr.user_id = ?
        WHERE mine.election_id = ?
        ORDER BY e.title
        """.trimIndent(), { rs, _ -> OtherBallot(rs.getLong("id"), rs.getString("title")) }, userId, electionId
    ).firstOrNull()

    /** Refuses a vote on [electionId] when the user voted on another ballot sharing its included election. */
    fun requireNoOtherBallot(electionId: Long, userId: Long) {
        val other = otherBallotVoted(electionId, userId)
        if (other != null) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "You need to \"Cancel\" your vote from the other midterm you submitted (\"${other.title}\"). " +
                    "You need to know your state voting district and choose the right election."
            )
        }
    }

    /**
     * Takes back the user's votes on this ballot: its own races, its included
     * elections' races, and their ballot measures. The included elections are
     * shared with the other district ballots, so if the user voted through
     * another one, those votes are theirs and stay. Each poll's published
     * results lose this user's rows; nothing else in them changes, so a
     * cancel never reveals anyone else's vote.
     */
    @Transactional
    fun cancel(electionId: Long, user: User): Int {
        val shared = if (otherBallotVoted(electionId, user.id) == null) includes.of(electionId) else emptyList()
        val elections = listOf(electionId) + shared
        var removed = 0
        for (id in elections) {
            removed += jdbc.update(
                "DELETE FROM candidate_responses WHERE user_id = ? AND candidate_id IN (SELECT id FROM candidates WHERE election_id = ?)",
                user.id, id
            )
            withdrawFromSnapshot(PollKind.ELECTION, id, user.id)
            for (m in jdbc.queryForList("SELECT id FROM ballot_measures WHERE election_id = ?", Long::class.java, id)) {
                removed += jdbc.update("DELETE FROM ballot_responses WHERE user_id = ? AND measure_id = ?", user.id, m)
                withdrawFromSnapshot(PollKind.BALLOT_MEASURE, m, user.id)
            }
        }
        return removed
    }

    private fun withdrawFromSnapshot(kind: PollKind, pollId: Long, userId: Long) {
        val snap = jdbc.queryForList(
            "SELECT id FROM result_snapshots WHERE poll_type = ? AND poll_id = ?", Long::class.java, kind.name, pollId
        ).firstOrNull() ?: return
        if (jdbc.update("DELETE FROM result_snapshot_answers WHERE snapshot_id = ? AND user_id = ?", snap, userId) > 0) {
            jdbc.update("UPDATE result_snapshots SET respondents = respondents - 1 WHERE id = ?", snap)
        }
    }
}

data class OtherBallot(val id: Long, val title: String)
