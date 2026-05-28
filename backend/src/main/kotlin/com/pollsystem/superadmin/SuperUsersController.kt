package com.pollsystem.superadmin

import com.pollsystem.email.EmailService
import com.pollsystem.model.AccessLevel
import com.pollsystem.model.RoleAssignment
import com.pollsystem.model.User
import com.pollsystem.model.UserMessage
import com.pollsystem.repository.CountyRepository
import com.pollsystem.repository.CountyZipsRepository
import com.pollsystem.repository.RoleAssignmentRepository
import com.pollsystem.repository.UserMessageRepository
import com.pollsystem.repository.UserRepository
import com.pollsystem.security.AppUserDetails
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/**
 * Single Super-admin view of every user account: filter by role / location,
 * enable or disable login, attach messages, demote an Admin back to Creator.
 * Replaces the older `/api/super/admins` controller, which only surfaced
 * Admins + Supers and tracked per-zipcode role-assignment toggles.
 *
 * The "Manage Users" UI in /super/manage-users binds to these endpoints.
 */
data class SuperUserRow(
    val id: Long,
    val email: String,
    val phone: String,
    val access: AccessLevel,
    val isEnabled: Boolean,
    val zipcode: String,
    val stateInitial: String?,
    val countyName: String?,
    val latestMessage: SuperUserMessageDto?
)

data class SuperUserMessageDto(
    val id: Long,
    val body: String,
    val emailed: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class CreateMessageRequest(val body: String, val sendEmail: Boolean = false)
data class EditMessageRequest(val body: String)
data class BulkToggleRequest(val userIds: List<Long>, val enable: Boolean)

/** Only User/Creator/Admin are listable here — VIEWER and SUPER are intentionally excluded. */
private val LISTABLE_ROLES = setOf(AccessLevel.USER, AccessLevel.CREATOR, AccessLevel.ADMIN)

@RestController
@RequestMapping("/api/super/users")
class SuperUsersController(
    private val users: UserRepository,
    private val counties: CountyRepository,
    private val countyZips: CountyZipsRepository,
    private val roleAssignments: RoleAssignmentRepository,
    private val userMessages: UserMessageRepository,
    private val emailService: EmailService
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun list(
        @RequestParam(name = "role", required = false) roles: List<String>?,
        @RequestParam(required = false, defaultValue = "false") includeDisabled: Boolean,
        @RequestParam(required = false) email: String?,
        @RequestParam(name = "stateId", required = false) stateIds: List<Long>?,
        @RequestParam(name = "countyId", required = false) countyIds: List<Long>?,
        @RequestParam(name = "zipcode", required = false) zipcodes: List<String>?
    ): List<SuperUserRow> {
        val parsedRoles = roles
            ?.mapNotNull { runCatching { AccessLevel.valueOf(it.uppercase()) }.getOrNull() }
            ?.filter { it in LISTABLE_ROLES }
            ?.toSet()
            ?: LISTABLE_ROLES
        if (parsedRoles.isEmpty()) return emptyList()

        var pool = users.findByAccessIn(parsedRoles.toList())
        if (!includeDisabled) pool = pool.filter { it.isEnabled }
        if (!email.isNullOrBlank()) {
            val needle = email.trim().lowercase()
            pool = pool.filter { it.email.lowercase().contains(needle) }
        }

        // Resolve effective zipcode filter: explicit zipcodes win, otherwise
        // expand county_ids → zipcodes, otherwise expand state_ids → zipcodes.
        val zipFilter: Set<String>? = when {
            !zipcodes.isNullOrEmpty() -> zipcodes.toSet()
            !countyIds.isNullOrEmpty() -> countyZips.findByCountyIdIn(countyIds).map { it.zipcode }.toSet()
            !stateIds.isNullOrEmpty() -> {
                val cIds = counties.findByStateIdIn(stateIds).map { it.id }
                if (cIds.isEmpty()) emptySet() else countyZips.findByCountyIdIn(cIds).map { it.zipcode }.toSet()
            }
            else -> null
        }
        if (zipFilter != null) pool = pool.filter { it.zipcode in zipFilter }

        if (pool.isEmpty()) return emptyList()

        // Batch-resolve geography + latest message for the result set.
        val poolZips = pool.map { it.zipcode }.distinct()
        val zipToCounty = countyZips.findByZipcodeIn(poolZips)
            .associate { it.zipcode to it.county }
        val latestByUser = userMessages.findByUserIdInOrderByCreatedAtDesc(pool.map { it.id })
            .groupBy { it.userId }
            .mapValues { it.value.first() }

        return pool.sortedBy { it.email }.map { u ->
            val county = zipToCounty[u.zipcode]
            SuperUserRow(
                id = u.id,
                email = u.email,
                phone = u.phone,
                access = u.access,
                isEnabled = u.isEnabled,
                zipcode = u.zipcode,
                stateInitial = county?.state?.initial,
                countyName = county?.name,
                latestMessage = latestByUser[u.id]?.let(::toDto)
            )
        }
    }

    /** Autocomplete source for the Email-contains filter. */
    @GetMapping("/emails")
    @Transactional(readOnly = true)
    fun emailSuggestions(@RequestParam prefix: String): List<String> {
        val trimmed = prefix.trim()
        if (trimmed.isEmpty()) return emptyList()
        return users.findByEmailStartingWithIgnoreCaseOrderByEmail(trimmed)
            .take(20)
            .map { it.email }
    }

    @PostMapping("/{userId}/toggle-enabled")
    @Transactional
    fun toggleEnabled(@PathVariable userId: Long): SuperUserRow {
        val u = users.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        guardListable(u)
        val saved = users.save(u.copy(isEnabled = !u.isEnabled))
        return rowFor(saved)
    }

    /** Shift-* / Shift-0 bulk toggle from the Manage Users table. */
    @PostMapping("/bulk-toggle-enabled")
    @Transactional
    fun bulkToggle(@RequestBody body: BulkToggleRequest): List<SuperUserRow> {
        if (body.userIds.isEmpty()) return emptyList()
        val pool = users.findAllById(body.userIds).filter { it.access in LISTABLE_ROLES }
        val saved = users.saveAll(pool.map { it.copy(isEnabled = body.enable) })
        return saved.sortedBy { it.email }.map(::rowFor)
    }

    @PostMapping("/{userId}/demote")
    @Transactional
    fun demote(@PathVariable userId: Long): SuperUserRow {
        val u = users.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        if (u.access == AccessLevel.SUPER) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot demote a Super")
        }
        if (u.access != AccessLevel.ADMIN) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Only Admins can be demoted here")
        }
        val updated = users.save(u.copy(access = AccessLevel.CREATOR))
        // Disable any ADMIN-role assignments alongside the demotion so the
        // user can't still act as Admin on those zipcodes via stale rows.
        roleAssignments.findByUserIdAndRole(userId, AccessLevel.ADMIN)
            .let { rows -> roleAssignments.saveAll(rows.map { ra: RoleAssignment -> ra.copy(enabled = false) }) }
        return rowFor(updated)
    }

    @GetMapping("/{userId}/messages")
    @Transactional(readOnly = true)
    fun listMessages(@PathVariable userId: Long): List<SuperUserMessageDto> {
        if (!users.existsById(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        return userMessages.findByUserIdOrderByCreatedAtDesc(userId).map(::toDto)
    }

    @PostMapping("/{userId}/messages")
    @Transactional
    fun createMessage(
        @PathVariable userId: Long,
        @RequestBody body: CreateMessageRequest,
        @AuthenticationPrincipal principal: AppUserDetails
    ): SuperUserMessageDto {
        val recipient = users.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        val text = body.body.trim()
        if (text.isEmpty() || text.length > 2000) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Message body must be 1-2000 chars")
        }
        var emailed = false
        if (body.sendEmail) {
            emailService.send(recipient.email, "A message from the Poll System", text)
            emailed = true
        }
        val saved = userMessages.save(
            UserMessage(
                userId = recipient.id,
                authorId = principal.user.id,
                body = text,
                emailed = emailed
            )
        )
        return toDto(saved)
    }

    @PutMapping("/messages/{messageId}")
    @Transactional
    fun editMessage(
        @PathVariable messageId: Long,
        @RequestBody body: EditMessageRequest
    ): SuperUserMessageDto {
        val existing = userMessages.findById(messageId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found")
        }
        val text = body.body.trim()
        if (text.isEmpty() || text.length > 2000) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Message body must be 1-2000 chars")
        }
        val saved = userMessages.save(existing.copy(body = text, updatedAt = Instant.now()))
        return toDto(saved)
    }

    private fun guardListable(u: User) {
        if (u.access !in LISTABLE_ROLES) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "User is not managed by /super/users")
        }
    }

    private fun rowFor(u: User): SuperUserRow {
        val county = countyZips.findByZipcode(u.zipcode).firstOrNull()?.county
        val latest = userMessages.findByUserIdOrderByCreatedAtDesc(u.id).firstOrNull()
        return SuperUserRow(
            id = u.id,
            email = u.email,
            phone = u.phone,
            access = u.access,
            isEnabled = u.isEnabled,
            zipcode = u.zipcode,
            stateInitial = county?.state?.initial,
            countyName = county?.name,
            latestMessage = latest?.let(::toDto)
        )
    }

    private fun toDto(m: UserMessage) = SuperUserMessageDto(
        id = m.id,
        body = m.body,
        emailed = m.emailed,
        createdAt = m.createdAt,
        updatedAt = m.updatedAt
    )
}
