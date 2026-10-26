package org.kodewerks.pollsystem.poll

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Which elections an election includes on its ballot (V37). One level only:
 * an included election doesn't include others, and a ballot that includes
 * others isn't itself included.
 */
@Component
class ElectionIncludes(private val jdbc: JdbcTemplate) {

    /** The elections [electionId] includes, in ballot order. */
    fun of(electionId: Long): List<Long> =
        jdbc.queryForList("select included_election_id from election_includes where election_id = ? order by position, included_election_id", Long::class.java, electionId)

    /** Every ballot's included elections, by ballot: one query for a whole search. */
    fun all(): Map<Long, List<Long>> =
        jdbc.query("select election_id, included_election_id from election_includes order by position, included_election_id") { rs, _ ->
            rs.getLong(1) to rs.getLong(2)
        }.groupBy({ it.first }, { it.second })

    /** The elections that include [electionId]. */
    fun includedBy(electionId: Long): List<Long> =
        jdbc.queryForList("select election_id from election_includes where included_election_id = ? order by election_id", Long::class.java, electionId)

    /** Elections included by a PUBLISHED ballot: kept out of poll search. */
    fun includedByPublished(): Set<Long> =
        jdbc.queryForList("""select distinct i.included_election_id from election_includes i
            join elections e on e.id = i.election_id where e.status = 'PUBLISHED'""", Long::class.java).toSet()

    fun replace(electionId: Long, included: List<Long>) {
        jdbc.update("delete from election_includes where election_id = ?", electionId)
        included.forEachIndexed { i, id ->
            jdbc.update("insert into election_includes (election_id, included_election_id, position) values (?, ?, ?)", electionId, id, i)
        }
    }
}
