package com.pollsystem.poll

import com.pollsystem.model.PollKind
import com.pollsystem.repository.PollTypeBlockRepository
import org.springframework.stereotype.Service

/**
 * Answers "is submissions blocked for this poll?" using the
 * `poll_type_blocks` table written by /admin/manage-polls. Blocks are
 * stored per-poll (see V15 migration), so a block on Electric Cars
 * doesn't affect Vaccines even when they share zipcodes.
 *
 * Used both as a hard gate on the response endpoints and as a filter on
 * the public search and results pages.
 */
@Service
class PollBlockService(
    private val blocks: PollTypeBlockRepository
) {

    /** Single-row gate: cheap for the response-controller hot path. */
    fun isBlocked(pollType: PollKind, pollId: Long): Boolean =
        blocks.existsByPollTypeAndPollId(pollType, pollId)

    /**
     * Bulk filter: returns only the rows whose poll is NOT blocked. Loads
     * blocks in one query and filters in memory, avoiding the N+1 a
     * per-row `isBlocked` would create on the search page.
     */
    fun <T> filterUnblocked(
        rows: List<T>,
        type: (T) -> PollKind,
        id: (T) -> Long
    ): List<T> {
        if (rows.isEmpty()) return rows
        val blockedKeys = blocks.findAll().map { it.pollType to it.pollId }.toSet()
        if (blockedKeys.isEmpty()) return rows
        return rows.filter { (type(it) to id(it)) !in blockedKeys }
    }
}
