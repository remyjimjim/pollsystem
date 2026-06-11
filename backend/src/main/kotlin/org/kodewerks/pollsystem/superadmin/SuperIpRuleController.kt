package org.kodewerks.pollsystem.superadmin

import org.kodewerks.pollsystem.model.IpRule
import org.kodewerks.pollsystem.model.IpRuleType
import org.kodewerks.pollsystem.repository.IpRuleRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

data class IpRuleInput(
    @field:NotBlank @field:Size(max = 64) val value: String,
    @field:NotNull val type: IpRuleType,
    val note: String? = null,
    val enabled: Boolean = true
)

data class IpRuleDto(
    val id: Long,
    val value: String,
    val type: IpRuleType,
    val note: String?,
    val enabled: Boolean,
    val createdAt: Instant,
    val createdByEmail: String?
) {
    companion object {
        fun from(r: IpRule) = IpRuleDto(
            id = r.id,
            value = r.value,
            type = r.type,
            note = r.note,
            enabled = r.enabled,
            createdAt = r.createdAt,
            createdByEmail = r.createdBy?.email
        )
    }
}

@RestController
@RequestMapping("/api/super/ip-rules")
class SuperIpRuleController(private val rules: IpRuleRepository) {

    @GetMapping
    @Transactional(readOnly = true)
    fun list(): List<IpRuleDto> =
        rules.findAll().sortedByDescending { it.createdAt }.map(IpRuleDto::from)

    @PostMapping
    @Transactional
    fun create(
        @AuthenticationPrincipal principal: AppUserDetails,
        @Valid @RequestBody body: IpRuleInput
    ): ResponseEntity<IpRuleDto> {
        validate(body.value)
        val saved = rules.save(
            IpRule(
                value = body.value.trim(),
                type = body.type,
                note = body.note?.trim(),
                enabled = body.enabled,
                createdBy = principal.user
            )
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(IpRuleDto.from(saved))
    }

    @PostMapping("/{id}/toggle")
    @Transactional
    fun toggle(@PathVariable id: Long): IpRuleDto {
        val r = rules.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "IP rule not found")
        }
        return IpRuleDto.from(rules.save(r.copy(enabled = !r.enabled)))
    }

    @DeleteMapping("/{id}")
    @Transactional
    fun delete(@PathVariable id: Long) {
        if (!rules.existsById(id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "IP rule not found")
        }
        rules.deleteById(id)
    }

    private fun validate(value: String) {
        // Light syntactic check — accept dotted IPv4, optional /CIDR, or IPv6 colon form.
        // Intentionally permissive; real enforcement is the filter (not wired).
        val v = value.trim()
        val ipv4 = Regex("""^(\d{1,3}\.){3}\d{1,3}(/\d{1,2})?$""")
        val ipv6 = Regex("""^[0-9a-fA-F:]+(/\d{1,3})?$""")
        if (!ipv4.matches(v) && !ipv6.matches(v)) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Value must be an IP address or CIDR block"
            )
        }
    }
}
