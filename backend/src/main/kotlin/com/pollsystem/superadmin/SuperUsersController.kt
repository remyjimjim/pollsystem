package com.pollsystem.superadmin

import com.pollsystem.email.EmailService
import com.pollsystem.model.AccessLevel
import com.pollsystem.model.PollStatus
import com.pollsystem.model.RoleAssignment
import com.pollsystem.model.User
import com.pollsystem.model.UserMessage
import com.pollsystem.repository.BallotMeasureRepository
import com.pollsystem.repository.BallotResponseRepository
import com.pollsystem.repository.CandidateResponseRepository
import com.pollsystem.repository.CountyRepository
import com.pollsystem.repository.CountyZipsRepository
import com.pollsystem.repository.ElectionRepository
import com.pollsystem.repository.QuestionResponseRepository
import com.pollsystem.repository.QuestionnaireRepository
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
import java.time.ZoneOffset

/**
 * Single Super-admin view of every user account: filter by role / location,
 * enable or disable login, attach messages, demote an Admin back to Creator
 * or a Creator back to a plain User, and view the polls a user has either
 * authored or responded to.
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
data class EditUserRequest(val role: String?, val zipcode: String?)

/** A poll authored by a given user, used by GET /{userId}/polls-created. */
data class SuperUserPollCreated(
    val type: String,
    val id: Long,
    val title: String,
    val status: PollStatus,
    val createdAt: Instant?,
    val closeDate: Instant?
)

/** A poll a user has responded to, used by GET /{userId}/polls-completed. */
data class SuperUserPollCompleted(
    val type: String,
    val id: Long,
    val title: String,
    val status: PollStatus,
    val lastSubmittedAt: Instant
)

/** One row in the answer-detail modal for a specific completed poll. */
data class SuperUserPollAnswer(
    val prompt: String,
    val answer: String,
    val comment: String?,
    val submittedAt: Instant
)

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
    private val emailService: EmailService,
    private val roleAssignmentBulkOps: RoleAssignmentBulkOps,
    private val questionnaires: QuestionnaireRepository,
    private val elections: ElectionRepository,
    private val ballotMeasures: BallotMeasureRepository,
    private val questionResponses: QuestionResponseRepository,
    private val candidateResponses: CandidateResponseRepository,
    private val ballotResponses: BallotResponseRepository
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun list(
        @RequestParam(name = "role", required = false) roles: List<String>?,
        @RequestParam(required = false, defaultValue = "false") includeDisabled: Boolean,
        @RequestParam(required = false) email: String?,
        @RequestParam(name = "stateId", required = false) stateIds: List<Long>?,
        @RequestParam(name = "countyId", required = false) countyIds: List<Long>?,
        @RequestParam(name = "zipcode", required = false) zipcodes: List<String>?,
        @RequestParam(required = false) message: String?
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

        // Free-text search over the message history. Resolves to the set
        // of user ids that have at least one matching body, then narrows
        // the pool to that set.
        if (!message.isNullOrBlank()) {
            val matchedUserIds = userMessages.findUserIdsWithBodyContaining(message.trim()).toSet()
            pool = pool.filter { it.id in matchedUserIds }
        }

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

    /**
     * Edit a listable user's role and/or zipcode. Both fields are
     * optional — only the fields the super-admin actually changes get
     * persisted. The role is restricted to LISTABLE_ROLES so this
     * endpoint can't create or unmake a SUPER. When the role changes,
     * every existing RoleAssignment for the user is updated to the new
     * role so the per-zipcode rows stay aligned with the access level.
     */
    @PutMapping("/{userId}")
    @Transactional
    fun edit(
        @PathVariable userId: Long,
        @RequestBody body: EditUserRequest
    ): SuperUserRow {
        val u = users.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        guardListable(u)

        val newAccess = body.role?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
            val parsed = runCatching { AccessLevel.valueOf(raw.uppercase()) }.getOrNull()
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role: $raw")
            if (parsed !in LISTABLE_ROLES) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Role must be one of USER/CREATOR/ADMIN")
            }
            parsed
        }
        val newZipcode = body.zipcode?.trim()?.takeIf { it.isNotEmpty() }?.also { zip ->
            if (countyZips.findByZipcode(zip).isEmpty()) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown zipcode: $zip")
            }
        }
        if (newAccess == null && newZipcode == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Nothing to update")
        }

        // Snapshot the old access level BEFORE the save — JPA's merge
        // mutates `u`'s backing field even though the Kotlin property is
        // `val`, so a post-save `u.access` already reflects the new
        // value and a `newAccess != u.access` check would always be
        // false.
        val previousAccess = u.access
        val updated = users.save(
            u.copy(
                access = newAccess ?: u.access,
                zipcode = newZipcode ?: u.zipcode
            )
        )
        // Mirror the role change onto every existing assignment so the
        // per-zipcode rows reflect the user's new access level. Per the
        // super-admin's directive, all of the user's RoleAssignments are
        // rewritten — not just the ones currently matching the old role.
        if (newAccess != null && newAccess != previousAccess) {
            roleAssignmentBulkOps.updateRoleForUser(userId, newAccess)
        }
        return rowFor(updated)
    }

    @PostMapping("/{userId}/demote")
    @Transactional
    fun demote(@PathVariable userId: Long): SuperUserRow {
        val u = users.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        val newAccess = when (u.access) {
            AccessLevel.ADMIN -> AccessLevel.CREATOR
            AccessLevel.CREATOR -> AccessLevel.USER
            AccessLevel.SUPER ->
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot demote a Super")
            else ->
                throw ResponseStatusException(HttpStatus.CONFLICT, "Only Admins or Creators can be demoted")
        }
        val updated = users.save(u.copy(access = newAccess))
        // Disable any assignments at the OLD access level alongside the
        // demotion so the user can't still act in that role on those
        // zipcodes via stale rows.
        roleAssignments.findByUserIdAndRole(userId, u.access)
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

    /**
     * Every poll authored by this user, across all three poll types.
     * Used by the "polls" link on a Creator/Admin/Super row in
     * /super/manage-users to surface what they've built.
     */
    @GetMapping("/{userId}/polls-created")
    @Transactional(readOnly = true)
    fun pollsCreated(@PathVariable userId: Long): List<SuperUserPollCreated> {
        if (!users.existsById(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        val rows = mutableListOf<SuperUserPollCreated>()
        questionnaires.findByCreatorId(userId).forEach {
            rows += SuperUserPollCreated(
                type = "questionnaire",
                id = it.id,
                title = it.title,
                status = it.status,
                createdAt = it.createDate.atStartOfDay().toInstant(ZoneOffset.UTC),
                closeDate = it.closeDate
            )
        }
        elections.findByCreatorId(userId).forEach {
            // Election has no dateCreated column; dateSubmitted is when it
            // was put into the system and is what /super/polls uses as its
            // creation-ish ordering proxy.
            rows += SuperUserPollCreated(
                type = "election",
                id = it.id,
                title = it.title,
                status = it.status,
                createdAt = it.dateSubmitted,
                closeDate = it.closeDate
            )
        }
        ballotMeasures.findByCreatorId(userId).forEach {
            rows += SuperUserPollCreated(
                type = "ballot-measure",
                id = it.id,
                title = it.title,
                status = it.status,
                createdAt = it.dateCreated,
                closeDate = it.closeDate
            )
        }
        return rows.sortedByDescending { it.createdAt ?: Instant.EPOCH }
    }

    /**
     * Every poll this user has responded to. Deduplicates per (type, id),
     * since a Questionnaire or Election yields multiple per-question /
     * per-candidate response rows. lastSubmittedAt is the most recent
     * dateSubmitted across that poll's responses by this user.
     */
    @GetMapping("/{userId}/polls-completed")
    @Transactional(readOnly = true)
    fun pollsCompleted(@PathVariable userId: Long): List<SuperUserPollCompleted> {
        if (!users.existsById(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        val rows = mutableListOf<SuperUserPollCompleted>()

        questionResponses.findByUserId(userId)
            .groupBy { it.question.questionnaire.id }
            .forEach { (_, group) ->
                val q = group.first().question.questionnaire
                rows += SuperUserPollCompleted(
                    type = "questionnaire",
                    id = q.id,
                    title = q.title,
                    status = q.status,
                    lastSubmittedAt = group.maxOf { it.dateSubmitted }
                )
            }
        candidateResponses.findByUserId(userId)
            .groupBy { it.candidate.election.id }
            .forEach { (_, group) ->
                val e = group.first().candidate.election
                rows += SuperUserPollCompleted(
                    type = "election",
                    id = e.id,
                    title = e.title,
                    status = e.status,
                    lastSubmittedAt = group.maxOf { it.dateSubmitted }
                )
            }
        ballotResponses.findByUserId(userId).forEach { br ->
            rows += SuperUserPollCompleted(
                type = "ballot-measure",
                id = br.measure.id,
                title = br.measure.title,
                status = br.measure.status,
                lastSubmittedAt = br.dateSubmitted
            )
        }
        return rows.sortedByDescending { it.lastSubmittedAt }
    }

    /**
     * The user's actual answers for one completed poll instance. Shape
     * varies by type: questionnaire returns one row per answered question,
     * election returns one row per candidate the user voted on, ballot
     * measure returns a single row (one yes/no per measure).
     */
    @GetMapping("/{userId}/polls-completed/{type}/{pollId}/answers")
    @Transactional(readOnly = true)
    fun pollAnswers(
        @PathVariable userId: Long,
        @PathVariable type: String,
        @PathVariable pollId: Long
    ): List<SuperUserPollAnswer> {
        if (!users.existsById(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }
        return when (type) {
            "questionnaire" -> questionResponses
                .findByQuestionnaireIdAndUserId(pollId, userId)
                .sortedBy { it.question.id }
                .map { qr ->
                    SuperUserPollAnswer(
                        prompt = qr.question.question,
                        answer = qr.response,
                        comment = qr.comment,
                        submittedAt = qr.dateSubmitted
                    )
                }
            "election" -> candidateResponses
                .findByElectionIdAndUserId(pollId, userId)
                .sortedBy { it.candidate.id }
                .map { cr ->
                    SuperUserPollAnswer(
                        prompt = "${cr.candidate.name} (${cr.candidate.affiliation})",
                        answer = if (cr.response) "Yes" else "No",
                        comment = cr.comment,
                        submittedAt = cr.dateSubmitted
                    )
                }
            "ballot-measure" -> ballotResponses
                .findByUserIdAndMeasureId(userId, pollId)
                ?.let { br ->
                    listOf(
                        SuperUserPollAnswer(
                            prompt = br.measure.title,
                            answer = if (br.response) "Yes" else "No",
                            comment = br.comment,
                            submittedAt = br.dateSubmitted
                        )
                    )
                } ?: emptyList()
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown poll type: $type")
        }
    }

    private fun toDto(m: UserMessage) = SuperUserMessageDto(
        id = m.id,
        body = m.body,
        emailed = m.emailed,
        createdAt = m.createdAt,
        updatedAt = m.updatedAt
    )
}
