package com.pollsystem.superadmin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.pollsystem.repository.PollTypeRepository
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class PollTypeAdminDto(
    val id: Long,
    val name: String,
    val pollType: Int,
    val template: JsonNode
)

@RestController
@RequestMapping("/api/super/poll-types")
class SuperPollTypeController(
    private val pollTypes: PollTypeRepository,
    private val objectMapper: ObjectMapper
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun list(): List<PollTypeAdminDto> =
        pollTypes.findAll().sortedBy { it.id }.map {
            PollTypeAdminDto(
                id = it.id,
                name = it.name,
                pollType = it.pollType,
                template = objectMapper.readTree(it.templateJson)
            )
        }

    @PutMapping("/{id}/template")
    @Transactional
    fun updateTemplate(
        @PathVariable id: Long,
        @RequestBody body: JsonNode
    ): PollTypeAdminDto {
        val pt = pollTypes.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Poll type not found")
        }
        val json = try {
            objectMapper.writeValueAsString(body)
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON template")
        }
        val saved = pollTypes.save(pt.copy(templateJson = json))
        return PollTypeAdminDto(
            id = saved.id,
            name = saved.name,
            pollType = saved.pollType,
            template = objectMapper.readTree(saved.templateJson)
        )
    }
}
