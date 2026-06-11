package org.kodewerks.pollsystem.geography

import org.kodewerks.pollsystem.model.County
import org.kodewerks.pollsystem.model.CountyZips
import org.kodewerks.pollsystem.model.State

data class StateDto(val id: Long, val name: String, val initial: String) {
    companion object {
        fun from(s: State) = StateDto(s.id, s.name, s.initial)
    }
}

data class CountyDto(val id: Long, val stateId: Long, val name: String) {
    companion object {
        fun from(c: County) = CountyDto(c.id, c.state.id, c.name)
    }
}

data class CountyZipDto(val id: Long, val countyId: Long, val zipcode: String) {
    companion object {
        fun from(z: CountyZips) = CountyZipDto(z.id, z.county.id, z.zipcode)
    }
}
