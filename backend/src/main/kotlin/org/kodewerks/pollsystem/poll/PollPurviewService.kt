package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.PollPurview
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.repository.BallotMeasureRepository
import org.kodewerks.pollsystem.repository.CountyZipsRepository
import org.kodewerks.pollsystem.repository.PollPurviewRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * A poll's PURVIEW (territorial scope) and the membership test that drives both
 * the within/outside-purview results split and zip-based search. Purview is
 * distinct from the admin block / kill-switch features.
 *
 * Membership is computed by scope LEVEL (never by expanding NATIONAL to every
 * zip): NATIONAL matches everyone; STATE matches the zip's state; COUNTY the
 * zip's county; ZIP an exact zip. A poll with NO purview rows is treated as
 * NATIONAL (nationwide). Ballot measures store no rows of their own — they
 * inherit their parent election's purview.
 */
@Service
class PollPurviewService(
    private val purviews: PollPurviewRepository,
    private val countyZips: CountyZipsRepository,
    private val ballotMeasures: BallotMeasureRepository
) {

    /** The purview rows governing this poll (ballot measures resolve to their election's). */
    @Transactional(readOnly = true)
    fun purviewOf(kind: PollKind, pollId: Long): List<PollPurview> {
        if (kind == PollKind.BALLOT_MEASURE) {
            val electionId = ballotMeasures.findById(pollId).map { it.election.id }.orElse(null)
                ?: return emptyList()
            return purviews.findByPollTypeAndPollId(PollKind.ELECTION, electionId)
        }
        return purviews.findByPollTypeAndPollId(kind, pollId)
    }

    /**
     * Is [zip] inside the purview described by [rows]? Used both to classify a
     * respondent (within/outside purview) and to decide whether a search for a
     * zip should surface the poll. No rows = nationwide = within.
     */
    fun includesZip(rows: List<PollPurview>, zip: String?): Boolean {
        if (rows.isEmpty()) return true
        if (rows.any { it.scopeLevel == ScopeLevel.NATIONAL }) return true
        // A respondent with no zipcode can't be placed inside a sub-national purview.
        if (zip == null) return false
        if (rows.any { it.scopeLevel == ScopeLevel.ZIP && it.zipcode == zip }) return true
        // COUNTY / STATE need the zip resolved to its county + state.
        val cz = countyZips.findByZipcode(zip).firstOrNull() ?: return false
        val countyId = cz.county.id
        val stateId = cz.county.state.id
        return rows.any {
            (it.scopeLevel == ScopeLevel.COUNTY && it.countyId == countyId) ||
                (it.scopeLevel == ScopeLevel.STATE && it.stateId == stateId)
        }
    }

    /** Convenience: does this poll's purview include [zip]? */
    fun includesZip(kind: PollKind, pollId: Long, zip: String?): Boolean =
        includesZip(purviewOf(kind, pollId), zip)
}
