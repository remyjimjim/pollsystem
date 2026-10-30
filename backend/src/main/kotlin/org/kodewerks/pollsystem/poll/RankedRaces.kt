package org.kodewerks.pollsystem.poll

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Which races are ranked-choice (V44, decided 2026-10-30): an office within
 * an election. Offices are shared across elections, so the flag is the pair.
 */
@Component
class RankedRaces(private val jdbc: JdbcTemplate) {

    /** The ranked offices of [electionId]. */
    fun of(electionId: Long): Set<Long> =
        jdbc.queryForList("select office_id from ranked_races where election_id = ?", Long::class.java, electionId).toSet()

    /** Every ranked race, by election: for counting results in bulk. */
    fun all(): Map<Long, Set<Long>> =
        jdbc.query("select election_id, office_id from ranked_races") { rs, _ -> rs.getLong(1) to rs.getLong(2) }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }

    /** Replaces [electionId]'s ranked offices. */
    fun set(electionId: Long, officeIds: Collection<Long>) {
        jdbc.update("delete from ranked_races where election_id = ?", electionId)
        officeIds.distinct().forEach { jdbc.update("insert into ranked_races (election_id, office_id) values (?, ?)", electionId, it) }
    }
}
