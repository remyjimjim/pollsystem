package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.geography.Region
import org.kodewerks.pollsystem.geography.RegionService
import org.kodewerks.pollsystem.model.BlockScope
import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.PollTypeBlock
import org.kodewerks.pollsystem.repository.PollTypeBlockRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Answers "are submissions blocked for this poll, HERE?" using the
 * `poll_type_blocks` table written by /admin/manage-polls and
 * /admin/manage-creators. Blocks are stored per-poll (see V15 migration), so a
 * block on Electric Cars doesn't affect Vaccines even when they share zipcodes.
 *
 * Blocks are area-aware: a ZIPCODE / COUNTY / STATE block applies only to
 * respondents (or searches) inside that area; an EVERYWHERE block shuts the
 * poll for everyone and also hides its public results.
 */
@Service
class PollBlockService(
    private val blocks: PollTypeBlockRepository,
    private val regions: RegionService
) {

    /**
     * Submission gate: is this poll blocked for a respondent in [zipcode]? A
     * respondent with no zipcode is only stopped by an EVERYWHERE block.
     */
    @Transactional(readOnly = true)
    fun isBlockedFor(pollType: PollKind, pollId: Long, zipcode: String?): Boolean {
        val found = blocks.findByPollTypeAndPollId(pollType, pollId)
        if (found.isEmpty()) return false
        if (found.any { it.scope == BlockScope.EVERYWHERE }) return true
        val here = zipcode?.let { regions.zip(it) } ?: return false
        return found.any { b -> regions.ofBlock(b)?.contains(here) == true }
    }

    /** Results gate: only an EVERYWHERE block hides a poll's public results. */
    fun isBlockedEverywhere(pollType: PollKind, pollId: Long): Boolean =
        blocks.findByPollTypeAndPollIdAndScope(pollType, pollId, BlockScope.EVERYWHERE).isNotEmpty()

    /**
     * Search filter: drops polls blocked EVERYWHERE, and, when the search is
     * narrowed to [searchedZips], polls blocked in every one of those zips.
     * Loads blocks in one query and resolves the zips in one more, avoiding the
     * N+1 a per-row check would create on the search page.
     */
    @Transactional(readOnly = true)
    fun <T> filterUnblocked(
        rows: List<T>,
        type: (T) -> PollKind,
        id: (T) -> Long,
        searchedZips: Collection<String>? = null
    ): List<T> {
        if (rows.isEmpty()) return rows
        val byPoll: Map<Pair<PollKind, Long>, List<PollTypeBlock>> =
            blocks.findAll().groupBy { it.pollType to it.pollId }
        if (byPoll.isEmpty()) return rows
        val zipRegions: Collection<Region>? = searchedZips?.takeIf { it.isNotEmpty() }?.let { regions.zips(it).values }
        val blockRegionCache = mutableMapOf<Long, Region?>()
        return rows.filter { row ->
            val polls = byPoll[type(row) to id(row)] ?: return@filter true
            if (polls.any { it.scope == BlockScope.EVERYWHERE }) return@filter false
            if (zipRegions.isNullOrEmpty()) return@filter true
            val areas = polls.mapNotNull { b -> blockRegionCache.getOrPut(b.id) { regions.ofBlock(b) } }
            // Keep the poll if any searched zip is still open.
            zipRegions.any { z -> areas.none { it.contains(z) } }
        }
    }
}
