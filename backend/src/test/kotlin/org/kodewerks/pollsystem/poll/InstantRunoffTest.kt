package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The instant-runoff count's rules (decided 2026-10-30). */
class InstantRunoffTest {
    private val a = InstantRunoff.Candidate(1, "Ann")
    private val b = InstantRunoff.Candidate(2, "Bob")
    private val c = InstantRunoff.Candidate(3, "Cal")
    private val d = InstantRunoff.Candidate(4, "Dee")
    private val all = listOf(a, b, c, d)
    private fun ballots(vararg spec: Pair<Int, List<Long>>) = spec.flatMap { (n, order) -> List(n) { order } }

    @Test
    fun `a majority of 1st choices wins in round 1`() {
        val r = InstantRunoff.count(ballots(6 to listOf(1L), 3 to listOf(2L), 1 to listOf(3L)), all)
        assertThat(r).hasSize(1)
        assertThat(r[0].winner).isEqualTo(1L)
        assertThat(r[0].votes).isEqualTo(mapOf(1L to 6, 2L to 3, 3L to 1, 4L to 0))
    }

    @Test
    fun `last place is eliminated and its ballots move to their next choice, until someone has a majority`() {
        // Round 1: Ann 4, Bob 3, Cal 2, Dee 1 -> Dee out (to Cal) -> Ann 4, Bob 3, Cal 3 -> Bob and Cal tie for last:
        // Bob had more in round 1, so Cal goes; Cal's ballots: 2 to Bob, 1 exhausted -> Ann 4, Bob 5: Bob wins.
        val r = InstantRunoff.count(ballots(
            4 to listOf(1L), 3 to listOf(2L), 2 to listOf(3L, 2L), 1 to listOf(4L, 3L)), all)
        assertThat(r.map { it.eliminated }).containsExactly(4L, 3L, null)
        assertThat(r.last().votes).isEqualTo(mapOf(1L to 4, 2L to 5))
        assertThat(r.last().exhausted).isEqualTo(1)
        assertThat(r.last().winner).isEqualTo(2L)
    }

    @Test
    fun `a tie for last with no earlier difference eliminates the later name alphabetically`() {
        val r = InstantRunoff.count(ballots(4 to listOf(1L), 2 to listOf(2L), 2 to listOf(3L)), listOf(a, b, c))   // no majority: 4 of 8
        assertThat(r.first().eliminated).isEqualTo(3L)   // Bob and Cal tie at 2: Cal (later name) goes
    }

    @Test
    fun `the final two stop the count, and a tie there goes to the earlier round's leader, then the earlier name`() {
        val r = InstantRunoff.count(ballots(2 to listOf(1L), 2 to listOf(2L)), listOf(a, b))
        assertThat(r).hasSize(1)
        assertThat(r[0].winner).isEqualTo(1L)   // 2-2: Ann before Bob
    }

    @Test
    fun `no ranked ballots gives no rounds`() {
        assertThat(InstantRunoff.count(emptyList(), all)).isEmpty()
        assertThat(InstantRunoff.count(listOf(emptyList()), all)).isEmpty()
    }
}
