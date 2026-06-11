package org.kodewerks.pollsystem.poll

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

/** Describes the geo filter for `filterApplied`. Used by the three Results DTOs. */
internal fun describeFilter(
    zipcodes: List<String>?,
    stateIds: List<Long>?,
    countyIds: List<Long>?,
    onlyPurview: Boolean
): Map<String, String>? = buildMap {
    zipcodes?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
        ?.let { put("zipcode", it.joinToString(",")) }
    countyIds?.takeIf { it.isNotEmpty() }
        ?.let { put("countyId", it.joinToString(",")) }
    stateIds?.takeIf { it.isNotEmpty() }
        ?.let { put("stateId", it.joinToString(",")) }
    if (onlyPurview) put("onlyPurview", "true")
}.takeIf { it.isNotEmpty() }
