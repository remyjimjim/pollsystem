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
    fun listCounties(@RequestParam("state_id") stateId: Long): List<CountyDto> =
        counties.findByStateId(stateId)
            .sortedBy { it.name }
            .map(CountyDto::from)

    @GetMapping("/zipcodes")
    fun listZipcodes(
        @RequestParam("county_ids", required = false) countyIds: List<Long>?,
        @RequestParam("state_id", required = false) stateId: Long?
    ): List<CountyZipDto> {
        // When state_id is set without county_ids, expand to every county
        // in that state — lets the search page populate the zipcode picker
        // with the full state list when the user leaves county at "Any".
        val ids = when {
            !countyIds.isNullOrEmpty() -> countyIds
            stateId != null -> counties.findByStateId(stateId).map { it.id }
            else -> return emptyList()
        }
        if (ids.isEmpty()) return emptyList()
        return countyZips.findByCountyIdIn(ids)
            .sortedBy { it.zipcode }
            .map(CountyZipDto::from)
    }
}
