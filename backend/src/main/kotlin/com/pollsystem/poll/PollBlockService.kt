package com.pollsystem.poll

import com.pollsystem.model.BlockScope
import com.pollsystem.model.CountyZips
import com.pollsystem.model.PollKind
import com.pollsystem.model.PollTypeBlock
import com.pollsystem.repository.CountyZipsRepository
import com.pollsystem.repository.PollTypeBlockRepository
import org.springframework.stereotype.Service

/**
 * Answers "is submissions blocked for this poll?" using the
 * `poll_type_blocks` table written by /admin/manage-polls. A block hits
 * if any of three scopes match the poll's zipcode set: the zipcode
 * directly, the county containing it, or the state containing it.
 *
 * Used both as a hard gate on the response endpoints and as a filter on
 * the public search and results pages. Questionnaires can span multiple
 * zipcodes via QuestionnaireDomain — any one of them being blocked
 * blocks the whole questionnaire.
 */
@Service
class PollBlockService(
    private val blocks: PollTypeBlockRepository,
    private val countyZips: CountyZipsRepository
) {

    /** Single-row gate: cheap for the response-controller hot path. */
    fun isBlocked(pollType: PollKind, zipcodes: Collection<String>): Boolean {
        if (zipcodes.isEmpty()) return false
        val typeBlocks = blocks.findByPollType(pollType)
        if (typeBlocks.isEmpty()) return false
        val zipMeta = countyZips.findByZipcodeIn(zipcodes.toList()).groupBy { it.zipcode }
        return zipcodes.any { matches(typeBlocks, it, zipMeta) }
    }

    /**
     * Bulk filter: returns only the rows whose poll is NOT blocked. Avoids
     * the N-queries pattern that a per-row `isBlocked` call would create on
     * the search page.
     */
    fun <T> filterUnblocked(
        rows: List<T>,
        type: (T) -> PollKind,
        zipcodes: (T) -> Collection<String>
    ): List<T> {
        if (rows.isEmpty()) return rows
        val typesPresent = rows.map(type).distinct()
        val typeBlocks = blocks.findByPollTypeIn(typesPresent).groupBy { it.pollType }
        if (typeBlocks.isEmpty()) return rows
        val allZips = rows.flatMap(zipcodes).distinct()
        val zipMeta = countyZips.findByZipcodeIn(allZips).groupBy { it.zipcode }
        return rows.filter { row ->
            val tb = typeBlocks[type(row)] ?: return@filter true
            zipcodes(row).none { matches(tb, it, zipMeta) }
        }
    }

    private fun matches(
        typeBlocks: List<PollTypeBlock>,
        zipcode: String,
        zipMeta: Map<String, List<CountyZips>>
    ): Boolean {
        if (typeBlocks.any { it.scope == BlockScope.ZIPCODE && it.zipcode == zipcode }) return true
        val meta = zipMeta[zipcode].orEmpty()
        val countyIds = meta.map { it.county.id }.toSet()
        if (typeBlocks.any { it.scope == BlockScope.COUNTY && it.countyId in countyIds }) return true
        val stateIds = meta.map { it.county.state.id }.toSet()
        return typeBlocks.any { it.scope == BlockScope.STATE && it.stateId in stateIds }
    }
}
