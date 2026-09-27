package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.PollPurview
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.repository.BallotMeasureRepository
import org.kodewerks.pollsystem.repository.CountyRepository
import org.kodewerks.pollsystem.repository.CountyZipsRepository
import org.kodewerks.pollsystem.repository.PollPurviewRepository
import org.kodewerks.pollsystem.repository.StateRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

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
    private val ballotMeasures: BallotMeasureRepository,
    private val states: StateRepository,
    private val counties: CountyRepository
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

    /**
     * Classify each of [zips] as within (true) / outside (false) [rows],
     * batch-resolving geography in ONE query (avoids the N+1 that a per-zip
     * [includesZip] would create when partitioning many respondents). A null or
     * blank zip is "outside" unless the purview is nationwide.
     */
    fun classifyZips(rows: List<PollPurview>, zips: Collection<String?>): Map<String?, Boolean> {
        val nationwide = rows.isEmpty() || rows.any { it.scopeLevel == ScopeLevel.NATIONAL }
        if (nationwide) return zips.toSet().associateWith { true }
        val zipRows = rows.filter { it.scopeLevel == ScopeLevel.ZIP }.mapNotNull { it.zipcode }.toSet()
        val countyRows = rows.filter { it.scopeLevel == ScopeLevel.COUNTY }.mapNotNull { it.countyId }.toSet()
        val stateRows = rows.filter { it.scopeLevel == ScopeLevel.STATE }.mapNotNull { it.stateId }.toSet()
        val realZips = zips.filterNotNull().filter { it.isNotBlank() }.distinct()
        val meta = if (realZips.isEmpty()) emptyMap() else countyZips.findByZipcodeIn(realZips).associateBy { it.zipcode }
        return zips.toSet().associateWith { zip ->
            when {
                zip.isNullOrBlank() -> false
                zip in zipRows -> true
                else -> meta[zip]?.let { it.county.id in countyRows || it.county.state.id in stateRows } == true
            }
        }
    }

    /**
     * Replace a poll's purview rows (delete-then-insert, mirroring
     * QuestionnaireService.replaceDomains). Fans a {scopeLevel, regionIds,
     * zipcodes} selection out into rows, setting only the level's own geo column
     * (per the V22 CHECK). Ballot measures inherit their election's purview, so
     * writing rows for one is rejected.
     */
    @Transactional
    fun replacePurview(
        kind: PollKind,
        pollId: Long,
        scopeLevel: ScopeLevel,
        regionIds: List<Long> = emptyList(),
        zipcodes: List<String> = emptyList()
    ) {
        if (kind == PollKind.BALLOT_MEASURE) {
            throw bad("Ballot measures inherit their election's purview")
        }
        purviews.deleteByPollTypeAndPollId(kind, pollId)
        purviews.saveAll(buildRows(kind, pollId, scopeLevel, regionIds, zipcodes))
    }

    private fun buildRows(
        kind: PollKind,
        pollId: Long,
        scopeLevel: ScopeLevel,
        regionIds: List<Long>,
        zipcodes: List<String>
    ): List<PollPurview> = when (scopeLevel) {
        ScopeLevel.NATIONAL ->
            listOf(PollPurview(pollType = kind, pollId = pollId, scopeLevel = ScopeLevel.NATIONAL))

        ScopeLevel.STATE -> {
            val ids = regionIds.distinct()
            if (ids.isEmpty()) throw bad("Select at least one state")
            val found = states.findAllById(ids)
            if (found.count() != ids.size) throw bad("One or more states are unknown")
            found.map { PollPurview(pollType = kind, pollId = pollId, scopeLevel = ScopeLevel.STATE, stateId = it.id) }
        }

        ScopeLevel.COUNTY -> {
            val ids = regionIds.distinct()
            if (ids.isEmpty()) throw bad("Select at least one county")
            val found = counties.findAllById(ids)
            if (found.count() != ids.size) throw bad("One or more counties are unknown")
            found.map { PollPurview(pollType = kind, pollId = pollId, scopeLevel = ScopeLevel.COUNTY, countyId = it.id) }
        }

        ScopeLevel.ZIP -> {
            val zips = zipcodes.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
            if (zips.isEmpty()) throw bad("Select at least one zipcode")
            val known = countyZips.findByZipcodeIn(zips).map { it.zipcode }.toSet()
            val unknown = zips.filterNot { it in known }
            if (unknown.isNotEmpty()) throw bad("Unknown zipcodes: $unknown")
            zips.map { PollPurview(pollType = kind, pollId = pollId, scopeLevel = ScopeLevel.ZIP, zipcode = it) }
        }
    }

    private fun bad(msg: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, msg)
}
