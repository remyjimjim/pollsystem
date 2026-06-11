package org.kodewerks.pollsystem.adminpolls

import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.BlockScope
import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.PollNote
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.model.PollTypeBlock
import org.kodewerks.pollsystem.repository.BallotMeasureRepository
import org.kodewerks.pollsystem.repository.CountyRepository
import org.kodewerks.pollsystem.repository.CountyZipsRepository
import org.kodewerks.pollsystem.repository.ElectionRepository
import org.kodewerks.pollsystem.repository.PollNoteRepository
import org.kodewerks.pollsystem.repository.PollTypeBlockRepository
import org.kodewerks.pollsystem.repository.QuestionnaireDomainRepository
import org.kodewerks.pollsystem.repository.QuestionnaireRepository
import org.kodewerks.pollsystem.repository.RoleAssignmentRepository
import org.kodewerks.pollsystem.repository.StateRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
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
 * Backs /admin/manage-polls. Each row is one poll instance the admin can
 * see in their purview (one or more `RoleAssignment` rows with role=ADMIN).
 * Admins can attach a stack of notes per poll and toggle submission
 * blocks at zipcode / county / state level. Super admins see every poll
 * regardless of role-assignments so they can audit the system.
 */
data class AdminPollRow(
    val id: Long,
    val type: String,            // "ELECTION" / "QUESTIONNAIRE" / "BALLOT_MEASURE"
    val title: String,
    val status: PollStatus,
    val creatorEmail: String,
    val closeDate: Instant?,
    val zipcodes: List<String>,
    val stateInitial: String?,
    val countyName: String?,
    val blocked: Boolean,
    val latestNote: NoteDto?
)

data class NoteDto(
    val id: Long,
    val body: String,
    val emailed: Boolean,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class PurviewDto(
    val states: List<StateOption>,
    val counties: List<CountyOption>,
    val zipcodes: List<String>,
    val unrestricted: Boolean
)
data class StateOption(val id: Long, val name: String, val initial: String)
data class CountyOption(val id: Long, val stateId: Long, val name: String)

data class CreateBlockRequest(val scope: BlockScope, val zipcode: String?, val countyId: Long?, val stateId: Long?)
data class CreateNoteRequest(val body: String, val sendEmail: Boolean = false)
data class EditNoteRequest(val body: String)

data class BlockDto(
    val id: Long,
    val scope: BlockScope,
    val zipcode: String?,
    val countyId: Long?,
    val countyName: String?,
    val stateId: Long?,
    val stateInitial: String?,
    val createdAt: Instant,
    val createdBy: Long
)

@RestController
@RequestMapping("/api/admin/polls")
class AdminPollsController(
    private val questionnaires: QuestionnaireRepository,
    private val elections: ElectionRepository,
    private val ballotMeasures: BallotMeasureRepository,
    private val domains: QuestionnaireDomainRepository,
    private val countyZips: CountyZipsRepository,
    private val counties: CountyRepository,
    private val states: StateRepository,
    private val roleAssignments: RoleAssignmentRepository,
    private val blocks: PollTypeBlockRepository,
    private val notes: PollNoteRepository,
    private val emailService: org.kodewerks.pollsystem.email.EmailService
) {

    @GetMapping("/purview")
    @Transactional(readOnly = true)
    fun purview(@AuthenticationPrincipal principal: AppUserDetails): PurviewDto {
        if (principal.user.access >= AccessLevel.SUPER) {
            val allStates = states.findAll().map { StateOption(it.id, it.name, it.initial) }
            // Don't ship all 30k+ zipcodes by default for super; let the picker
            // load them on demand via the standard /api/zipcodes lookup.
            return PurviewDto(allStates, emptyList(), emptyList(), unrestricted = true)
        }
        val mine = roleAssignments
            .findByUserIdAndRole(principal.user.id, AccessLevel.ADMIN)
            .filter { it.enabled }
        if (mine.isEmpty()) return PurviewDto(emptyList(), emptyList(), emptyList(), unrestricted = false)
        val stateOpts = mine.map { it.state }.distinctBy { it.id }
            .sortedBy { it.name }
            .map { StateOption(it.id, it.name, it.initial) }
        val countyOpts = mine.map { it.county }.distinctBy { it.id }
            .sortedBy { it.name }
            .map { CountyOption(it.id, it.state.id, it.name) }
        val zips = mine.map { it.zipcode }.distinct().sorted()
        return PurviewDto(stateOpts, countyOpts, zips, unrestricted = false)
    }

    @GetMapping
    @Transactional(readOnly = true)
    fun list(
        @AuthenticationPrincipal principal: AppUserDetails,
        @RequestParam(name = "pollType", required = false) pollTypes: List<String>?,
        @RequestParam(required = false) title: String?,
        @RequestParam(name = "stateId", required = false) stateIds: List<Long>?,
        @RequestParam(name = "countyId", required = false) countyIds: List<Long>?,
        @RequestParam(name = "zipcode", required = false) zipcodes: List<String>?,
        @RequestParam(required = false) notesContain: String?,
        @RequestParam(required = false, defaultValue = "false") includeDisabled: Boolean
    ): List<AdminPollRow> {
        val purview = resolvePurview(principal)
        if (purview != null && purview.isEmpty()) return emptyList()

        val parsedKinds = pollTypes
            ?.mapNotNull { runCatching { PollKind.valueOf(it.uppercase()) }.getOrNull() }
            ?.toSet()
            ?: PollKind.values().toSet()
        if (parsedKinds.isEmpty()) return emptyList()

        // Resolve the effective explicit-zip filter from the optional state /
        // county / zipcode params, same precedence as PollSearchController.
        val explicitZipFilter: Set<String>? = when {
            !zipcodes.isNullOrEmpty() -> zipcodes.toSet()
            !countyIds.isNullOrEmpty() -> countyZips.findByCountyIdIn(countyIds).map { it.zipcode }.toSet()
            !stateIds.isNullOrEmpty() -> {
                val cIds = counties.findByStateIdIn(stateIds).map { it.id }
                if (cIds.isEmpty()) emptySet() else countyZips.findByCountyIdIn(cIds).map { it.zipcode }.toSet()
            }
            else -> null
        }

        val needle = title?.trim()?.takeIf { it.isNotEmpty() }
        val rows = mutableListOf<AdminPollRow>()
        val zipMetaFor = { zips: Collection<String> ->
            if (zips.isEmpty()) emptyMap()
            else countyZips.findByZipcodeIn(zips.toList()).associateBy { it.zipcode }
        }

        if (PollKind.QUESTIONNAIRE in parsedKinds) {
            val qPool = questionnaires.findAll()
            for (q in qPool) {
                if (needle != null && !q.title.contains(needle, ignoreCase = true)) continue
                val qZips = domains.findByQuestionnaireId(q.id).map { it.zipcode }.distinct()
                if (!matchesGeo(qZips, purview, explicitZipFilter)) continue
                val meta = zipMetaFor(qZips)
                rows += AdminPollRow(
                    id = q.id,
                    type = PollKind.QUESTIONNAIRE.name,
                    title = q.title,
                    status = q.status,
                    creatorEmail = q.creator.email,
                    closeDate = q.closeDate,
                    zipcodes = qZips.sorted(),
                    stateInitial = qZips.firstOrNull()?.let { meta[it]?.county?.state?.initial },
                    countyName = qZips.firstOrNull()?.let { meta[it]?.county?.name },
                    blocked = false,
                    latestNote = null
                )
            }
        }
        if (PollKind.ELECTION in parsedKinds) {
            val pool = elections.findAll()
            for (e in pool) {
                if (needle != null && !e.title.contains(needle, ignoreCase = true)) continue
                if (!matchesGeo(listOf(e.zipcode), purview, explicitZipFilter)) continue
                val cz = countyZips.findByZipcode(e.zipcode).firstOrNull()
                rows += AdminPollRow(
                    id = e.id, type = PollKind.ELECTION.name, title = e.title, status = e.status,
                    creatorEmail = e.creator.email, closeDate = e.closeDate,
                    zipcodes = listOf(e.zipcode),
                    stateInitial = cz?.county?.state?.initial,
                    countyName = cz?.county?.name,
                    blocked = false, latestNote = null
                )
            }
        }
        if (PollKind.BALLOT_MEASURE in parsedKinds) {
            val pool = ballotMeasures.findAll()
            for (bm in pool) {
                if (needle != null && !bm.title.contains(needle, ignoreCase = true)) continue
                val zip = bm.election.zipcode
                if (!matchesGeo(listOf(zip), purview, explicitZipFilter)) continue
                val cz = countyZips.findByZipcode(zip).firstOrNull()
                rows += AdminPollRow(
                    id = bm.id, type = PollKind.BALLOT_MEASURE.name, title = bm.title, status = bm.status,
                    creatorEmail = bm.creator.email, closeDate = bm.closeDate,
                    zipcodes = listOf(zip),
                    stateInitial = cz?.county?.state?.initial,
                    countyName = cz?.county?.name,
                    blocked = false, latestNote = null
                )
            }
        }

        // Compute blocked + latestNote in one pass per kind.
        val withBlocks = rows.map { r ->
            val kind = PollKind.valueOf(r.type)
            r.copy(blocked = isBlockedFor(kind, r.id))
        }
        val byKind = withBlocks.groupBy { PollKind.valueOf(it.type) }
        val latestByKey = mutableMapOf<Pair<PollKind, Long>, PollNote>()
        for ((kind, group) in byKind) {
            if (group.isEmpty()) continue
            val ids = group.map { it.id }
            val noteRows = notes.findByPollTypeAndPollIdInOrderByCreatedAtDesc(kind, ids)
            for (n in noteRows) {
                val key = kind to n.pollId
                if (key !in latestByKey) latestByKey[key] = n
            }
        }

        var final = withBlocks.map { r ->
            val key = PollKind.valueOf(r.type) to r.id
            r.copy(latestNote = latestByKey[key]?.let { toDto(it) })
        }
        if (!includeDisabled) final = final.filter { !it.blocked }
        if (!notesContain.isNullOrBlank()) {
            val keys = notes.findPollKeysWithBodyContaining(notesContain.trim())
                .map { (it[0] as PollKind) to (it[1] as Long) }
                .toSet()
            final = final.filter { (PollKind.valueOf(it.type) to it.id) in keys }
        }
        return final.sortedBy { it.title }
    }

    @GetMapping("/{type}/{id}/blocks")
    @Transactional(readOnly = true)
    fun listBlocks(@PathVariable type: String, @PathVariable id: Long): List<BlockDto> {
        val (kind, _) = locatePoll(type, id)
        return blocks.findByPollTypeAndPollId(kind, id).map(::toBlockDto)
    }

    @PostMapping("/{type}/{id}/block")
    @Transactional
    fun createBlock(
        @PathVariable type: String,
        @PathVariable id: Long,
        @RequestBody body: CreateBlockRequest,
        @AuthenticationPrincipal principal: AppUserDetails
    ): BlockDto {
        val (kind, zips) = locatePoll(type, id)
        val purview = resolvePurview(principal)
        val saved = when (body.scope) {
            BlockScope.ZIPCODE -> {
                val zip = body.zipcode ?: throw bad("zipcode required for ZIPCODE scope")
                if (zip !in zips) throw bad("Zipcode $zip is not one of this poll's zipcodes")
                requirePurviewZipcode(purview, zip)
                val existing = blocks.findByPollTypeAndPollIdAndScopeAndZipcode(kind, id, BlockScope.ZIPCODE, zip)
                if (existing != null) return toBlockDto(existing)
                blocks.save(PollTypeBlock(
                    pollType = kind, pollId = id, scope = BlockScope.ZIPCODE,
                    zipcode = zip, createdBy = principal.user.id
                ))
            }
            BlockScope.COUNTY -> {
                val cId = body.countyId ?: throw bad("countyId required for COUNTY scope")
                requirePurviewCounty(purview, cId)
                val existing = blocks.findByPollTypeAndPollIdAndScopeAndCountyId(kind, id, BlockScope.COUNTY, cId)
                if (existing != null) return toBlockDto(existing)
                blocks.save(PollTypeBlock(
                    pollType = kind, pollId = id, scope = BlockScope.COUNTY,
                    countyId = cId, createdBy = principal.user.id
                ))
            }
            BlockScope.STATE -> {
                val sId = body.stateId ?: throw bad("stateId required for STATE scope")
                requirePurviewState(purview, sId)
                val existing = blocks.findByPollTypeAndPollIdAndScopeAndStateId(kind, id, BlockScope.STATE, sId)
                if (existing != null) return toBlockDto(existing)
                blocks.save(PollTypeBlock(
                    pollType = kind, pollId = id, scope = BlockScope.STATE,
                    stateId = sId, createdBy = principal.user.id
                ))
            }
        }
        return toBlockDto(saved)
    }

    @DeleteMapping("/blocks/{blockId}")
    @Transactional
    fun deleteBlock(@PathVariable blockId: Long, @AuthenticationPrincipal principal: AppUserDetails) {
        val b = blocks.findById(blockId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Block not found")
        }
        val purview = resolvePurview(principal)
        when (b.scope) {
            BlockScope.ZIPCODE -> b.zipcode?.let { requirePurviewZipcode(purview, it) }
            BlockScope.COUNTY -> b.countyId?.let { requirePurviewCounty(purview, it) }
            BlockScope.STATE -> b.stateId?.let { requirePurviewState(purview, it) }
        }
        blocks.delete(b)
    }

    @GetMapping("/{type}/{id}/notes")
    @Transactional(readOnly = true)
    fun listNotes(@PathVariable type: String, @PathVariable id: Long): List<NoteDto> {
        val kind = parseKind(type)
        return notes.findByPollTypeAndPollIdOrderByCreatedAtDesc(kind, id).map(::toDto)
    }

    @PostMapping("/{type}/{id}/notes")
    @Transactional
    fun createNote(
        @PathVariable type: String,
        @PathVariable id: Long,
        @RequestBody body: CreateNoteRequest,
        @AuthenticationPrincipal principal: AppUserDetails
    ): NoteDto {
        val (kind, _) = locatePoll(type, id)
        val text = body.body.trim()
        if (text.isEmpty() || text.length > 2000) throw bad("Note must be 1–2000 chars")
        var emailed = false
        if (body.sendEmail) {
            val creatorEmail = creatorEmailFor(kind, id)
            emailService.send(creatorEmail, "A note about your poll", text)
            emailed = true
        }
        val saved = notes.save(PollNote(
            pollType = kind, pollId = id, body = text,
            authorId = principal.user.id, emailed = emailed
        ))
        return toDto(saved)
    }

    @PutMapping("/notes/{noteId}")
    @Transactional
    fun editNote(@PathVariable noteId: Long, @RequestBody body: EditNoteRequest): NoteDto {
        val existing = notes.findById(noteId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Note not found")
        }
        val text = body.body.trim()
        if (text.isEmpty() || text.length > 2000) throw bad("Note must be 1–2000 chars")
        val saved = notes.save(existing.copy(body = text, updatedAt = Instant.now()))
        return toDto(saved)
    }

    // ---------- internal helpers ----------

    private data class Purview(val zipcodes: Set<String>, val countyIds: Set<Long>, val stateIds: Set<Long>) {
        fun isEmpty() = zipcodes.isEmpty() && countyIds.isEmpty() && stateIds.isEmpty()
    }

    /** Returns null when the principal is unrestricted (SUPER); empty Purview means "no purview". */
    private fun resolvePurview(principal: AppUserDetails): Purview? {
        if (principal.user.access >= AccessLevel.SUPER) return null
        val mine = roleAssignments
            .findByUserIdAndRole(principal.user.id, AccessLevel.ADMIN)
            .filter { it.enabled }
        return Purview(
            zipcodes = mine.map { it.zipcode }.toSet(),
            countyIds = mine.map { it.county.id }.toSet(),
            stateIds = mine.map { it.state.id }.toSet()
        )
    }

    private fun matchesGeo(
        pollZips: List<String>,
        purview: Purview?,
        explicit: Set<String>?
    ): Boolean {
        if (purview != null) {
            // Restrict to admin's purview at zip-or-county-or-state granularity.
            val ok = pollZips.any { zip ->
                if (zip in purview.zipcodes) return@any true
                val meta = countyZips.findByZipcode(zip)
                meta.any { it.county.id in purview.countyIds || it.county.state.id in purview.stateIds }
            }
            if (!ok) return false
        }
        if (explicit != null) {
            return pollZips.any { it in explicit }
        }
        return true
    }

    private fun locatePoll(type: String, id: Long): Pair<PollKind, List<String>> {
        val kind = parseKind(type)
        val zips = when (kind) {
            PollKind.QUESTIONNAIRE -> {
                questionnaires.findById(id).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Questionnaire not found")
                }
                domains.findByQuestionnaireId(id).map { it.zipcode }.distinct()
            }
            PollKind.ELECTION -> listOf(elections.findById(id).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Election not found")
            }.zipcode)
            PollKind.BALLOT_MEASURE -> listOf(ballotMeasures.findById(id).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Ballot measure not found")
            }.election.zipcode)
        }
        return kind to zips
    }

    /** Address used when emailing a copy of the note to the poll's creator. */
    private fun creatorEmailFor(kind: PollKind, id: Long): String = when (kind) {
        PollKind.QUESTIONNAIRE -> questionnaires.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Questionnaire not found")
        }.creator.email
        PollKind.ELECTION -> elections.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Election not found")
        }.creator.email
        PollKind.BALLOT_MEASURE -> ballotMeasures.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Ballot measure not found")
        }.creator.email
    }

    private fun parseKind(type: String): PollKind = runCatching { PollKind.valueOf(type.uppercase()) }
        .getOrElse { throw bad("Unknown poll type: $type") }

    private fun requirePurviewZipcode(purview: Purview?, zipcode: String) {
        if (purview == null) return
        if (zipcode in purview.zipcodes) return
        val meta = countyZips.findByZipcode(zipcode)
        if (meta.any { it.county.id in purview.countyIds || it.county.state.id in purview.stateIds }) return
        throw ResponseStatusException(HttpStatus.FORBIDDEN, "Zipcode $zipcode is outside your purview")
    }
    private fun requirePurviewCounty(purview: Purview?, countyId: Long) {
        if (purview == null) return
        if (countyId in purview.countyIds) return
        val state = counties.findById(countyId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "County not found")
        }.state
        if (state.id in purview.stateIds) return
        throw ResponseStatusException(HttpStatus.FORBIDDEN, "County is outside your purview")
    }
    private fun requirePurviewState(purview: Purview?, stateId: Long) {
        if (purview == null) return
        if (stateId !in purview.stateIds) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "State is outside your purview")
        }
    }

    private fun isBlockedFor(kind: PollKind, pollId: Long): Boolean =
        blocks.existsByPollTypeAndPollId(kind, pollId)

    private fun toBlockDto(b: PollTypeBlock): BlockDto {
        val county = b.countyId?.let { counties.findById(it).orElse(null) }
        val state = b.stateId?.let { states.findById(it).orElse(null) }
            ?: county?.state
        return BlockDto(
            id = b.id, scope = b.scope, zipcode = b.zipcode,
            countyId = b.countyId, countyName = county?.name,
            stateId = b.stateId, stateInitial = state?.initial,
            createdAt = b.createdAt, createdBy = b.createdBy
        )
    }

    private fun toDto(n: PollNote) = NoteDto(
        id = n.id, body = n.body, emailed = n.emailed,
        createdAt = n.createdAt, updatedAt = n.updatedAt
    )

    private fun bad(msg: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, msg)
}
