package org.kodewerks.pollsystem.poll

/**
 * The instant-runoff count for one ranked-choice race (decided 2026-10-30,
 * docs/VOTING-RANKED-CHOICE.md). Each ballot counts for its highest-ranked
 * candidate still in the count. A candidate with more than half of the
 * ballots still counting wins; otherwise the last-placed is eliminated and
 * the count repeats. With two left, the higher wins (as Alaska and Maine do).
 *
 * Ties: for last place, the one with fewer votes in the previous round goes,
 * then the round before, and so on, then the later name alphabetically. For
 * first between the final two, the reverse. Deterministic, so the same
 * results show every time (real elections draw lots).
 */
object InstantRunoff {
    data class Candidate(val id: Long, val name: String)

    /**
     * One round: votes per candidate still in the count, ballots with no
     * choice left (exhausted), who was eliminated after it, and the winner on
     * the last round.
     */
    data class Round(val votes: Map<Long, Int>, val exhausted: Int, val eliminated: Long? = null, val winner: Long? = null)

    /** [ballots]: each voter's candidate ids, 1st choice first. Empty when nobody ranked anyone. */
    fun count(ballots: List<List<Long>>, candidates: List<Candidate>): List<Round> {
        val counted = ballots.filter { it.isNotEmpty() }
        if (counted.isEmpty() || candidates.isEmpty()) return emptyList()
        val byId = candidates.associateBy { it.id }
        val continuing = candidates.map { it.id }.toMutableSet()
        val rounds = mutableListOf<Round>()
        while (true) {
            val votes = continuing.associateWith { 0 }.toMutableMap()
            var exhausted = 0
            for (b in counted) {
                val choice = b.firstOrNull { it in continuing }
                if (choice == null) exhausted++ else votes[choice] = votes.getValue(choice) + 1
            }
            val active = votes.values.sum()
            val leader = order(votes.keys, votes, rounds, byId).last()
            if (continuing.size <= 2 || votes.getValue(leader) * 2 > active || active == 0) {
                rounds += Round(votes, exhausted, winner = if (active == 0) null else leader)
                return rounds
            }
            val out = order(votes.keys, votes, rounds, byId).first()
            rounds += Round(votes, exhausted, eliminated = out)
            continuing -= out
        }
    }

    /** [ids] from weakest to strongest: this round's votes, then earlier rounds' (latest first), then later name is weaker. */
    private fun order(ids: Set<Long>, now: Map<Long, Int>, earlier: List<Round>, byId: Map<Long, Candidate>): List<Long> =
        ids.sortedWith(Comparator<Long> { a, b ->
            val c = now.getValue(a).compareTo(now.getValue(b))
            if (c != 0) return@Comparator c
            for (r in earlier.asReversed()) {
                val d = (r.votes[a] ?: 0).compareTo(r.votes[b] ?: 0)
                if (d != 0) return@Comparator d
            }
            byId.getValue(b).name.compareTo(byId.getValue(a).name)   // later name sorts weaker
        })
}
