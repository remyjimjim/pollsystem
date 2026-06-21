package org.kodewerks.pollsystem.geography

import org.kodewerks.pollsystem.repository.CountyRepository
import org.kodewerks.pollsystem.repository.CountyZipsRepository
import org.kodewerks.pollsystem.repository.StateRepository
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * POST body for /api/zipcodes. All three fields are optional and apply in
 * the same precedence the previous GET form did: prefix takes priority
 * (typeahead mode), then countyIds, then stateIds (expand to all counties
 * in those states). Sent as JSON so the request line stays short even
 * when countyIds carries thousands of entries — query-string GETs hit
 * Tomcat's request-line ceiling at around 3,000 IDs.
 */
data class ZipcodeQuery(
    val countyIds: List<Long>? = null,
    val stateIds: List<Long>? = null,
    val prefix: String? = null,
)

@RestController
@RequestMapping("/api")
class GeographyController(
    private val states: StateRepository,
    private val counties: CountyRepository,
    private val countyZips: CountyZipsRepository
) {

    @GetMapping("/states")
    fun listStates(): List<StateDto> =
        states.findAll(Sort.by("name")).map(StateDto::from)

    @GetMapping("/counties")
    fun listCounties(
        @RequestParam("state_id", required = false) stateIds: List<Long>?,
        @RequestParam("prefix", required = false) prefix: String?
    ): List<CountyDto> {
        // Prefix-search mode: the search page lets a user start typing
        // a county name without picking a state first. Cap matches at 50
        // so the dropdown stays scrollable — refine by typing more.
        if (!prefix.isNullOrBlank()) {
            return counties.findByNameStartingWithIgnoreCaseOrderByName(prefix.trim())
                .take(50)
                .map(CountyDto::from)
        }
        if (stateIds.isNullOrEmpty()) return emptyList()
        return counties.findByStateIdIn(stateIds)
            .sortedBy { it.name }
            .map(CountyDto::from)
    }

    @PostMapping("/zipcodes")
    fun listZipcodes(@RequestBody query: ZipcodeQuery): List<CountyZipDto> {
        // Prefix-search is its own mode: the search page lets a user start
        // typing a zip without picking a state first. Capped to 50 so the
        // dropdown stays scrollable; refine by typing more digits.
        if (!query.prefix.isNullOrBlank()) {
            return countyZips.findByZipcodeStartingWithOrderByZipcode(query.prefix.trim())
                .take(50)
                .map(CountyZipDto::from)
        }
        // When stateIds is set without countyIds, expand to every county
        // in those states — lets the search page populate the zipcode
        // picker with the full state-set list when county is at "Any".
        val ids = when {
            !query.countyIds.isNullOrEmpty() -> query.countyIds
            !query.stateIds.isNullOrEmpty() -> counties.findByStateIdIn(query.stateIds).map { it.id }
            else -> return emptyList()
        }
        if (ids.isEmpty()) return emptyList()
        return countyZips.findByCountyIdIn(ids)
            .sortedBy { it.zipcode }
            .map(CountyZipDto::from)
    }
}
