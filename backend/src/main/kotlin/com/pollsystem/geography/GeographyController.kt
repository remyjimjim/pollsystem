package com.pollsystem.geography

import com.pollsystem.repository.CountyRepository
import com.pollsystem.repository.CountyZipsRepository
import com.pollsystem.repository.StateRepository
import org.springframework.data.domain.Sort
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

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
    fun listCounties(@RequestParam("state_id") stateIds: List<Long>): List<CountyDto> {
        if (stateIds.isEmpty()) return emptyList()
        return counties.findByStateIdIn(stateIds)
            .sortedBy { it.name }
            .map(CountyDto::from)
    }

    @GetMapping("/zipcodes")
    fun listZipcodes(
        @RequestParam("county_ids", required = false) countyIds: List<Long>?,
        @RequestParam("state_id", required = false) stateIds: List<Long>?,
        @RequestParam("prefix", required = false) prefix: String?
    ): List<CountyZipDto> {
        // Prefix-search is its own mode: the search page lets a user start
        // typing a zip without picking a state first. Capped to 50 so the
        // dropdown stays scrollable; refine by typing more digits.
        if (!prefix.isNullOrBlank()) {
            return countyZips.findByZipcodeStartingWithOrderByZipcode(prefix.trim())
                .take(50)
                .map(CountyZipDto::from)
        }
        // When state_id is set without county_ids, expand to every county
        // in those states — lets the search page populate the zipcode
        // picker with the full state-set list when county is at "Any".
        val ids = when {
            !countyIds.isNullOrEmpty() -> countyIds
            !stateIds.isNullOrEmpty() -> counties.findByStateIdIn(stateIds).map { it.id }
            else -> return emptyList()
        }
        if (ids.isEmpty()) return emptyList()
        return countyZips.findByCountyIdIn(ids)
            .sortedBy { it.zipcode }
            .map(CountyZipDto::from)
    }
}
