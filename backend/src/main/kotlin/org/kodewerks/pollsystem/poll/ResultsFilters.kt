package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.PollPurview
import org.kodewerks.pollsystem.repository.CountyRepository
import org.kodewerks.pollsystem.repository.CountyZipsRepository

/**
 * Shared geo-filter resolution for the three Results endpoints. Mirrors
 * the precedence used by `PollSearchController.search`: explicit
 * zipcodes win, otherwise expand counties to their zips, otherwise
 * expand states to every zip under each state's counties.
 *
 * Returns null when no geo filter was requested — caller treats that as
 * "do not narrow by geography". Returns an empty set when the requested
 * filter resolves to no zipcodes (no responses can match).
 */
internal fun resolveGeoFilter(
    zipcodes: List<String>?,
    stateIds: List<Long>?,
    countyIds: List<Long>?,
    counties: CountyRepository,
    countyZips: CountyZipsRepository
): Set<String>? {
    val pickedZips = zipcodes?.filter { it.isNotBlank() }
    return when {
        !pickedZips.isNullOrEmpty() -> pickedZips.toSet()
        !countyIds.isNullOrEmpty() -> countyZips.findByCountyIdIn(countyIds).map { it.zipcode }.toSet()
        !stateIds.isNullOrEmpty() -> {
            val cIds = counties.findByStateIdIn(stateIds).map { it.id }
            if (cIds.isEmpty()) emptySet()
            else countyZips.findByCountyIdIn(cIds).map { it.zipcode }.toSet()
        }
        else -> null
    }
}

/**
 * Describes the active filter for `filterApplied`. Used by the three Results
 * DTOs. The purview split is reported only when it narrows (i.e. one of the two
 * groups is excluded); both-on is the unfiltered default and adds nothing.
 */
internal fun describeFilter(
    zipcodes: List<String>?,
    stateIds: List<Long>?,
    countyIds: List<Long>?,
    withinPurview: Boolean,
    outsidePurview: Boolean
): Map<String, String>? = buildMap {
    zipcodes?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
        ?.let { put("zipcode", it.joinToString(",")) }
    countyIds?.takeIf { it.isNotEmpty() }
        ?.let { put("countyId", it.joinToString(",")) }
    stateIds?.takeIf { it.isNotEmpty() }
        ?.let { put("stateId", it.joinToString(",")) }
    if (!withinPurview) put("withinPurview", "false")
    if (!outsidePurview) put("outsidePurview", "false")
}.takeIf { it.isNotEmpty() }

/**
 * Partition results by whether the respondent's zipcode falls inside the poll's
 * purview, keeping only the selected group(s). Both-on is the no-op default.
 * Batch-classifies via [PollPurviewService.classifyZips] (one geo query).
 */
internal fun <T> filterByPurview(
    rows: List<T>,
    zipOf: (T) -> String?,
    purviewRows: List<PollPurview>,
    withinPurview: Boolean,
    outsidePurview: Boolean,
    purviewService: PollPurviewService
): List<T> {
    if (withinPurview && outsidePurview) return rows
    val classified = purviewService.classifyZips(purviewRows, rows.map(zipOf))
    return rows.filter {
        val within = classified[zipOf(it)] ?: false
        (withinPurview && within) || (outsidePurview && !within)
    }
}
