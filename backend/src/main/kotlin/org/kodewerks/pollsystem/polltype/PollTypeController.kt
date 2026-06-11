package org.kodewerks.pollsystem.polltype

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.kodewerks.pollsystem.model.PollType
import org.kodewerks.pollsystem.repository.PollTypeRepository
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class PollTypeDto(val id: Long, val pollType: Int, val name: String) {
    companion object {
        fun from(pt: PollType) = PollTypeDto(pt.id, pt.pollType, pt.name)
    }
}

@RestController
@RequestMapping("/api/poll-types")
class PollTypeController(
    private val pollTypes: PollTypeRepository,
    private val objectMapper: ObjectMapper
) {
    @GetMapping
    fun list(): List<PollTypeDto> =
        pollTypes.findAll().sortedBy { it.id }.map(PollTypeDto::from)

    @GetMapping("/{id}/template")
    fun template(@PathVariable id: Long): JsonNode {
        val pt = pollTypes.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Poll type not found")
        }
        return objectMapper.readTree(pt.templateJson)
    }
}
