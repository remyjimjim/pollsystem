package org.kodewerks.pollsystem.adminpolls

import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.BlockScope
import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.PollNote
import org.kodewerks.pollsystem.model.PollPurview
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.model.PollTypeBlock
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.repository.BallotMeasureRepository
import org.kodewerks.pollsystem.repository.CountyRepository
import org.kodewerks.pollsystem.repository.CountyZipsRepository
import org.kodewerks.pollsystem.repository.ElectionRepository
import org.kodewerks.pollsystem.repository.PollNoteRepository
import org.kodewerks.pollsystem.repository.PollTypeBlockRepository
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
    /** Human-readable purview (e.g. "California", "Nationwide"); shown when a poll has no single zip. */
    val regionLabel: String,
    val blocked: Boolean,
    val latestNote: NoteDto?,
    /** Covers the whole country (or declares no area): hidden unless "Show nationwide". */
    val nationwide: Boolean = false,
    /** Questionnaires and ballot measures (elections have none): editable in Edit. */
    val summary: String? = null
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

/**
 * Admin moderation edit. A null field is left unchanged (Phase 1 does not clear
 * closeDate). `reason` is optional (since 2026-11-06) and recorded with what
 * changed as a poll note (the audit trail); `notifyCreator` emails the creator
 * a copy of that note.
 */
data class AdminPollEditRequest(
    val title: String? = null,
    /** Questionnaires and ballot measures only (moved here from the retired Super page, 2026-10-28). */
    val summary: String? = null,
    val status: PollStatus? = null,
    val closeDate: Instant? = null,
    val reason: String? = null,
    val notifyCreator: Boolean = false
)

/** One poll, by type (ELECTION / QUESTIONNAIRE / BALLOT_MEASURE) and id. */
data class PollRef(val type: String, val id: Long)

/** The polls listed on Manage Polls when the super pressed "Remove close dates". */
data class RemoveCloseDatesRequest(val polls: List<PollRef>)

/** How many close dates were removed; how many of those polls had closed and are open again. */
data class RemoveCloseDatesResult(val cleared: Int, val reopened: Int)

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
    private val purviews: org.kodewerks.pollsystem.poll.PollPurviewService,
    private val countyZips: CountyZipsRepository,
    private val counties: CountyRepository,
    private val states: StateRepository,
    private val roleAssignments: RoleAssignmentRepository,
    private val blocks: PollTypeBlockRepository,
    private val notes: PollNoteRepository,
    private val badWords: org.kodewerks.pollsystem.moderation.BadWords,
    private val emailService: org.kodewerks.pollsystem.email.EmailService,
    private val regions: org.kodewerks.pollsystem.geography.RegionService,
    private val history: org.kodewerks.pollsystem.poll.PollHistoryService,
    private val pollPurviews: org.kodewerks.pollsystem.repository.PollPurviewRepository,
    private val em: jakarta.persistence.EntityManager
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
        val stateOpts = mine.mapNotNull { it.state }.distinctBy { it.id }
            .sortedBy { it.name }
            .map { StateOption(it.id, it.name, it.initial) }
        val countyOpts = mine.mapNotNull { it.county }.distinctBy { it.id }
            .sortedBy { it.name }
            .map { CountyOption(it.id, it.state.id, it.name) }
        val zips = mine.mapNotNull { it.zipcode }.distinct().sorted()
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
        @RequestParam(required = false, defaultValue = "false") includeDisabled: Boolean,
        // Exact creator email (case-insensitive) — the Polls link on /admin/manage-creators.
        @RequestParam(required = false) creatorEmail: String? = null
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

        // Everything the rows need, loaded in a few bulk queries (2026-11-06).
        // It used to be several queries per poll (area, blocks, area names, and
        // one per zipcode for the state filter): ~2,000 per list, 5-10 s on
        // staging where every query crosses from Fly to Neon.
        val purviewsByPoll = pollPurviews.findAll().groupBy { it.pollType to it.pollId }
        fun purviewRowsOf(kind: PollKind, id: Long) = purviewsByPoll[kind to id].orEmpty()
        val electionPool = if (PollKind.ELECTION in parsedKinds || PollKind.BALLOT_MEASURE in parsedKinds) elections.findAll() else emptyList()
        val electionsById = electionPool.associateBy { it.id }
        val measurePool = if (PollKind.BALLOT_MEASURE in parsedKinds) ballotMeasures.findAll() else emptyList()
        val neededZips = buildSet {
            purviewsByPoll.values.forEach { r -> r.forEach { it.zipcode?.let(::add) } }
            electionPool.forEach { it.zipcode?.let(::add) }
            explicitZipFilter?.let(::addAll)
        }
        val geo = loadGeo(neededZips)
        val explicitArea = explicitZipFilter?.let { zs ->
            val known = zs.mapNotNull { geo.zipCounty[it] }
            ExplicitArea(zs, known.toSet(), known.mapNotNull { geo.countyState[it] }.toSet())
        }
        val label = { r: List<PollPurview> -> purviews.regionLabel(r, { geo.stateName[it] }, { geo.countyLabel[it] }) }
        val zipState = { z: String? -> z?.let { geo.zipCounty[it] }?.let { geo.countyState[it] }?.let { geo.stateInitial[it] } }
        val zipCountyName = { z: String? -> z?.let { geo.zipCounty[it] }?.let { geo.countyName[it] } }
        val matches = { r: List<PollPurview> ->
            (purview == null || overlapsPurview(r, purview, geo)) && (explicitArea == null || explicitArea.covers(r))
        }

        if (PollKind.QUESTIONNAIRE in parsedKinds) {
            for (q in questionnaires.findAll()) {
                if (needle != null && !q.title.contains(needle, ignoreCase = true)) continue
                val pollRows = purviewRowsOf(PollKind.QUESTIONNAIRE, q.id)
                if (!matches(pollRows)) continue
                val qZips = purviews.zipcodesOf(pollRows)
                rows += AdminPollRow(
                    id = q.id, type = PollKind.QUESTIONNAIRE.name, title = q.title, status = q.status,
                    creatorEmail = q.creator.email, closeDate = q.closeDate,
                    zipcodes = qZips,
                    stateInitial = zipState(qZips.firstOrNull()),
                    countyName = zipCountyName(qZips.firstOrNull()),
                    regionLabel = label(pollRows),
                    blocked = false, latestNote = null,
                    nationwide = isNationwide(pollRows),
                    summary = q.summary
                )
            }
        }
        if (PollKind.ELECTION in parsedKinds) {
            for (e in electionPool) {
                if (needle != null && !e.title.contains(needle, ignoreCase = true)) continue
                val pollRows = purviewRowsOf(PollKind.ELECTION, e.id)
                if (!matches(pollRows)) continue
                rows += AdminPollRow(
                    id = e.id, type = PollKind.ELECTION.name, title = e.title, status = e.status,
                    creatorEmail = e.creator.email, closeDate = e.closeDate,
                    zipcodes = listOfNotNull(e.zipcode),
                    stateInitial = zipState(e.zipcode),
                    countyName = zipCountyName(e.zipcode),
                    regionLabel = label(pollRows),
                    blocked = false, latestNote = null,
                    nationwide = isNationwide(pollRows)
                )
            }
        }
        if (PollKind.BALLOT_MEASURE in parsedKinds) {
            for (bm in measurePool) {
                if (needle != null && !bm.title.contains(needle, ignoreCase = true)) continue
                // A measure's area is its election's (as in PollPurviewService.purviewOf).
                val electionId = bm.election.id
                val pollRows = purviewRowsOf(PollKind.ELECTION, electionId)
                if (!matches(pollRows)) continue
                val zip = electionsById[electionId]?.zipcode
                rows += AdminPollRow(
                    id = bm.id, type = PollKind.BALLOT_MEASURE.name, title = bm.title, status = bm.status,
                    creatorEmail = bm.creator.email, closeDate = bm.closeDate, summary = bm.summary,
                    zipcodes = listOfNotNull(zip),
                    stateInitial = zipState(zip),
                    countyName = zipCountyName(zip),
                    regionLabel = label(pollRows),
                    blocked = false, latestNote = null,
                    nationwide = isNationwide(pollRows)
                )
            }
        }

        // "Blocked" means a block touches the caller's own purview (blocks are
        // area-aware). All blocks in one query; there are few of them.
        val mine = regions.adminRegions(principal.user)
        val blocksByPoll = blocks.findAll().groupBy { it.pollType to it.pollId }
        val withBlocks = rows.map { r ->
            val kind = PollKind.valueOf(r.type)
            r.copy(blocked = blocksByPoll[kind to r.id].orEmpty().any { b ->
                val area = regions.ofBlock(b) ?: return@any false
                mine.any { it.overlaps(area) }
            })
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
        creatorEmail?.trim()?.takeIf { it.isNotEmpty() }?.let { email ->
            final = final.filter { it.creatorEmail.equals(email, ignoreCase = true) }
        }
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
                // Any zip inside the poll's purview (not just zip-level polls' own zips):
                // blocks are area-aware, so this disables the poll for that zip only.
                if (zip !in zips && !purviews.includesZip(kind, id, zip)) {
                    throw bad("Zipcode $zip is not inside this poll's purview")
                }
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
            BlockScope.EVERYWHERE -> {
                // Whole-poll block: no zip/county/state discriminator. The admin
                // still needs purview overlapping the poll.
                if (!matchesAdminGeo(kind, id, purview, null)) {
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, "This poll is outside your purview")
                }
                val existing = blocks.findByPollTypeAndPollIdAndScope(kind, id, BlockScope.EVERYWHERE).firstOrNull()
                if (existing != null) return toBlockDto(existing)
                blocks.save(PollTypeBlock(
                    pollType = kind, pollId = id, scope = BlockScope.EVERYWHERE,
                    createdBy = principal.user.id
                ))
            }
        }
        history.record(kind, id, org.kodewerks.pollsystem.model.PollEventType.SWITCHED_OFF, principal.user, blockLabel(saved))
        return toBlockDto(saved)
    }

    /** Where a block applies, for the poll's history: "Everywhere", "State: California", … */
    private fun blockLabel(b: PollTypeBlock): String = when (b.scope) {
        BlockScope.EVERYWHERE -> "Everywhere"
        BlockScope.STATE -> "State: " + (b.stateId?.let { states.findById(it).orElse(null)?.name } ?: b.stateId)
        BlockScope.COUNTY -> "County: " + (b.countyId?.let { counties.findById(it).orElse(null)?.name } ?: b.countyId)
        BlockScope.ZIPCODE -> "Zipcode: ${b.zipcode}"
    }

    /** Whether this admin (or super) may see the poll: the same rule as the Manage Polls list. */
    fun canSee(principal: AppUserDetails, kind: PollKind, pollId: Long): Boolean =
        matchesAdminGeo(kind, pollId, resolvePurview(principal), null)

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
            BlockScope.EVERYWHERE -> {
                if (!matchesAdminGeo(b.pollType, b.pollId, purview, null)) {
                    throw ResponseStatusException(HttpStatus.FORBIDDEN, "This poll is outside your purview")
                }
            }
        }
        blocks.delete(b)
        history.record(b.pollType, b.pollId, org.kodewerks.pollsystem.model.PollEventType.SWITCHED_ON, principal.user, blockLabel(b))
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
        badWords.requireClean("the note" to text)
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
        badWords.requireClean("the note" to text)
        val saved = notes.save(existing.copy(body = text, updatedAt = Instant.now()))
        return toDto(saved)
    }

    /**
     * Moderation edit of a poll's status and/or close date, with a required
     * reason recorded as a note (and optionally emailed to the creator). Only
     * admins whose purview covers the poll (SUPER always) may edit it.
     */
    @PutMapping("/{type}/{id}")
    @Transactional
    fun editPoll(
        @PathVariable type: String,
        @PathVariable id: Long,
        @RequestBody body: AdminPollEditRequest,
        @AuthenticationPrincipal principal: AppUserDetails
    ): NoteDto {
        val (kind, _) = locatePoll(type, id)
        if (!matchesAdminGeo(kind, id, resolvePurview(principal), null)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "This poll is outside your purview")
        }
        // The reason is optional (2026-11-06); when given it's kept with the change.
        val reason = body.reason?.trim().orEmpty()
        if (reason.length > 2000) throw bad("A reason can be at most 2000 characters")
        val newTitle = body.title?.trim()
        if (newTitle != null && (newTitle.isEmpty() || newTitle.length > 500)) throw bad("A title must be 1–500 characters")
        val newSummary = body.summary?.trim()
        if (newSummary != null && newSummary.isEmpty()) throw bad("A summary can't be blank")
        if (newSummary != null && kind == PollKind.ELECTION) throw bad("Elections have no summary")
        // A ballot measure's summary is often official ballot language: the page asks, the server doesn't refuse it.
        badWords.requireClean(
            "the title" to newTitle,
            "the summary" to newSummary?.takeIf { kind != PollKind.BALLOT_MEASURE },
            "the reason" to reason.takeIf { it.isNotEmpty() }
        )
        if (newTitle == null && newSummary == null && body.status == null && body.closeDate == null) {
            throw bad("Nothing to change: provide a title, summary, status and/or close date")
        }

        // Apply the change (null = leave unchanged; Phase 1 doesn't clear closeDate).
        var oldTitle = ""
        var oldSummary: String? = null
        var oldClose: Instant? = null
        when (kind) {
            PollKind.QUESTIONNAIRE -> {
                val q = questionnaires.findById(id).orElseThrow { bad("Questionnaire not found") }
                oldTitle = q.title; oldSummary = q.summary; oldClose = q.closeDate
                questionnaires.save(q.copy(title = newTitle ?: q.title, summary = newSummary ?: q.summary, status = body.status ?: q.status, closeDate = body.closeDate ?: q.closeDate))
            }
            PollKind.ELECTION -> {
                val e = elections.findById(id).orElseThrow { bad("Election not found") }
                oldTitle = e.title; oldClose = e.closeDate
                elections.save(e.copy(title = newTitle ?: e.title, status = body.status ?: e.status, closeDate = body.closeDate ?: e.closeDate))
            }
            PollKind.BALLOT_MEASURE -> {
                val bm = ballotMeasures.findById(id).orElseThrow { bad("Ballot measure not found") }
                oldTitle = bm.title; oldSummary = bm.summary; oldClose = bm.closeDate
                ballotMeasures.save(bm.copy(
                    title = newTitle ?: bm.title,
                    summary = newSummary ?: bm.summary,
                    status = body.status ?: bm.status,
                    closeDate = body.closeDate ?: bm.closeDate,
                    lastUpdated = Instant.now()
                ))
            }
        }
        // An election's new close date carries to its ballot measures
        // (decided 2026-10-28): they close with it.
        val closeChanged = body.closeDate != null && body.closeDate != oldClose
        if (kind == PollKind.ELECTION && closeChanged) {
            for (m in ballotMeasures.findByElectionId(id).filter { it.closeDate != body.closeDate }) {
                ballotMeasures.save(m.copy(closeDate = body.closeDate, lastUpdated = Instant.now()))
                history.record(PollKind.BALLOT_MEASURE, m.id, org.kodewerks.pollsystem.model.PollEventType.EDITED, principal.user,
                    "Close date → ${body.closeDate} (from its election)")
            }
        }

        // Record the reason as a note (audit trail) — prefixed with what changed
        // so the creator sees the context — and optionally email the creator.
        val changes = buildList {
            if (newTitle != null && newTitle != oldTitle) add("title → \"$newTitle\"")
            if (newSummary != null && newSummary != oldSummary) add("summary changed")
            body.status?.let { add("status → $it") }
            body.closeDate?.let { add("close date → $it") }
        }
        val noteBody = "[${changes.joinToString(", ")}]" + if (reason.isEmpty()) "" else " $reason"
        var emailed = false
        if (body.notifyCreator) {
            emailService.send(creatorEmailFor(kind, id), "A change to your poll", noteBody)
            emailed = true
        }
        val saved = notes.save(PollNote(
            pollType = kind, pollId = id, body = noteBody,
            authorId = principal.user.id, emailed = emailed
        ))
        history.record(kind, id, org.kodewerks.pollsystem.model.PollEventType.EDITED, principal.user,
            (changes.map { it.replaceFirstChar(Char::uppercase) } + listOfNotNull(reason.takeIf { it.isNotEmpty() }?.let { "Reason: $it" }))
                .joinToString("\n"))
        return toDto(saved)
    }

    /**
     * "Remove close dates" (2026-11-06, SUPER only): clear the close date of each
     * poll the super has listed on Manage Polls, i.e. exactly the polls their
     * filters show. A poll whose date had passed (or whose status is CLOSED) is
     * open for voting again and its History gets a "Reopened" entry; one that
     * hadn't closed yet gets an "Edited" entry. Both say what the date was, so
     * the History keeps every poll's past close dates. An election's ballot
     * measures follow it, as with an edited close date (decided 2026-10-28).
     * Drafts and archived polls are left alone.
     */
    @PostMapping("/remove-close-dates")
    @Transactional
    fun removeCloseDates(
        @RequestBody body: RemoveCloseDatesRequest,
        @AuthenticationPrincipal principal: AppUserDetails
    ): RemoveCloseDatesResult {
        if (principal.user.access < AccessLevel.SUPER) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only a super can remove close dates")
        }
        if (body.polls.size > 10_000) throw bad("Too many polls at once")
        val now = Instant.now()
        val stamp = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(java.time.ZoneOffset.UTC)
        val done = mutableSetOf<Pair<PollKind, Long>>()
        var cleared = 0
        var reopened = 0
        /** Records one cleared date; returns whether the poll was clear-able. */
        fun clear(kind: PollKind, id: Long, status: PollStatus, old: Instant?, save: (PollStatus) -> Unit, why: String? = null) {
            if (!done.add(kind to id)) return
            if (status != PollStatus.PUBLISHED && status != PollStatus.CLOSED) return
            if (old == null && status != PollStatus.CLOSED) return
            val wasClosed = status == PollStatus.CLOSED || (old != null && !old.isAfter(now))
            save(PollStatus.PUBLISHED)
            cleared++
            if (wasClosed) reopened++
            val details = "Close date removed (was ${old?.let { stamp.format(it) } ?: "none"})" + (why?.let { ", $it" } ?: "")
            history.record(kind, id,
                if (wasClosed) org.kodewerks.pollsystem.model.PollEventType.REOPENED else org.kodewerks.pollsystem.model.PollEventType.EDITED,
                principal.user, details)
        }
        fun clearMeasure(m: org.kodewerks.pollsystem.model.BallotMeasure, why: String? = null) =
            clear(PollKind.BALLOT_MEASURE, m.id, m.status, m.closeDate, { st ->
                ballotMeasures.save(m.copy(status = st, closeDate = null, lastUpdated = now))
            }, why)

        for (ref in body.polls) {
            when (parseKind(ref.type)) {
                PollKind.QUESTIONNAIRE -> questionnaires.findById(ref.id).orElse(null)?.let { q ->
                    clear(PollKind.QUESTIONNAIRE, q.id, q.status, q.closeDate, { st -> questionnaires.save(q.copy(status = st, closeDate = null)) })
                }
                PollKind.ELECTION -> elections.findById(ref.id).orElse(null)?.let { e ->
                    clear(PollKind.ELECTION, e.id, e.status, e.closeDate, { st -> elections.save(e.copy(status = st, closeDate = null)) })
                    for (m in ballotMeasures.findByElectionId(e.id)) clearMeasure(m, "with its election")
                }
                PollKind.BALLOT_MEASURE -> ballotMeasures.findById(ref.id).orElse(null)?.let { clearMeasure(it) }
            }
        }
        return RemoveCloseDatesResult(cleared, reopened)
    }

    // ---------- internal helpers ----------

    private fun isNationwide(rows: List<org.kodewerks.pollsystem.model.PollPurview>) =
        rows.isEmpty() || rows.any { it.scopeLevel == ScopeLevel.NATIONAL }

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
            zipcodes = mine.mapNotNull { it.zipcode }.toSet(),
            countyIds = mine.mapNotNull { it.county?.id }.toSet(),
            stateIds = mine.mapNotNull { it.state?.id }.toSet()
        )
    }

    /**
     * Geo gate for the admin list + moderation, computed from the poll's PURVIEW
     * rows (so coarse County/State/National polls are matched, not just zip-level
     * ones). Two gates: the poll's purview must overlap the admin's reach
     * ([purview] null = SUPER, sees all), and — if the admin drilled the UI down
     * to specific zips ([explicit]) — the poll's purview must include one of them.
     */
    private fun matchesAdminGeo(
        pollRows: List<PollPurview>,
        purview: Purview?,
        explicit: Set<String>?
    ): Boolean {
        if (purview != null && !overlapsPurview(pollRows, purview)) return false
        if (explicit != null && !explicit.any { purviews.includesZip(pollRows, it) }) return false
        return true
    }

    /** Convenience: load [kind]/[pollId]'s purview rows, then [matchesAdminGeo]. */
    private fun matchesAdminGeo(kind: PollKind, pollId: Long, purview: Purview?, explicit: Set<String>?): Boolean =
        matchesAdminGeo(purviews.purviewOf(kind, pollId), purview, explicit)

    /** Place names and relations the list needs, loaded in bulk (see [list]). */
    private class Geo(
        val stateName: Map<Long, String>,
        val stateInitial: Map<Long, String>,
        val countyState: Map<Long, Long>,
        val countyName: Map<Long, String>,
        val countyLabel: Map<Long, String>,
        val zipCounty: Map<String, Long>
    )

    private fun loadGeo(zips: Set<String>): Geo {
        val st = states.findAll()
        val initial = st.associate { it.id to it.initial }
        val cRows = em.createQuery("select c.id, c.name, c.state.id from County c", Array<Any>::class.java).resultList
        val countyState = cRows.associate { (it[0] as Long) to (it[2] as Long) }
        val countyName = cRows.associate { (it[0] as Long) to (it[1] as String) }
        val zipCounty = HashMap<String, Long>()
        zips.chunked(5000).forEach { chunk ->
            em.createQuery("select cz.zipcode, cz.county.id from CountyZips cz where cz.zipcode in :z", Array<Any>::class.java)
                .setParameter("z", chunk).resultList
                .forEach { zipCounty.putIfAbsent(it[0] as String, it[1] as Long) }
        }
        return Geo(
            stateName = st.associate { it.id to it.name },
            stateInitial = initial,
            countyState = countyState,
            countyName = countyName,
            countyLabel = countyName.mapValues { (id, name) -> "$name (${initial[countyState[id]] ?: ""})" },
            zipCounty = zipCounty
        )
    }

    /**
     * The State / County / Zipcode filter as an area: its zipcodes and the
     * counties and states they lie in. A poll matches when its purview includes
     * any of those zipcodes, as [PollPurviewService.includesZip] would decide
     * zip by zip, without a query per zip.
     */
    private class ExplicitArea(val zips: Set<String>, val countyIds: Set<Long>, val stateIds: Set<Long>) {
        fun covers(rows: List<PollPurview>): Boolean {
            if (zips.isEmpty()) return false
            if (rows.isEmpty()) return true
            return rows.any {
                when (it.scopeLevel) {
                    ScopeLevel.NATIONAL -> true
                    ScopeLevel.ZIP -> it.zipcode in zips
                    ScopeLevel.COUNTY -> it.countyId in countyIds
                    ScopeLevel.STATE -> it.stateId in stateIds
                }
            }
        }
    }

    /** [overlapsPurview] using preloaded geography (no queries). */
    private fun overlapsPurview(pollRows: List<PollPurview>, admin: Purview, geo: Geo): Boolean {
        if (pollRows.isEmpty()) return true
        return pollRows.any { r ->
            when (r.scopeLevel) {
                ScopeLevel.NATIONAL -> true
                ScopeLevel.STATE -> r.stateId in admin.stateIds
                ScopeLevel.COUNTY -> r.countyId in admin.countyIds || geo.countyState[r.countyId] in admin.stateIds
                ScopeLevel.ZIP -> r.zipcode in admin.zipcodes ||
                    geo.zipCounty[r.zipcode]?.let { c -> c in admin.countyIds || geo.countyState[c] in admin.stateIds } == true
            }
        }
    }

    /** Does a poll's purview overlap the admin's reach? No purview rows = nationwide = visible to all. */
    private fun overlapsPurview(pollRows: List<PollPurview>, admin: Purview): Boolean {
        if (pollRows.isEmpty()) return true
        return pollRows.any { r ->
            when (r.scopeLevel) {
                ScopeLevel.NATIONAL -> true
                ScopeLevel.STATE -> r.stateId in admin.stateIds
                ScopeLevel.COUNTY -> r.countyId in admin.countyIds ||
                    counties.findById(r.countyId!!).map { it.state.id in admin.stateIds }.orElse(false)
                ScopeLevel.ZIP -> r.zipcode in admin.zipcodes ||
                    countyZips.findByZipcode(r.zipcode!!).firstOrNull()
                        ?.let { it.county.id in admin.countyIds || it.county.state.id in admin.stateIds } == true
            }
        }
    }

    private fun locatePoll(type: String, id: Long): Pair<PollKind, List<String>> {
        val kind = parseKind(type)
        val zips = when (kind) {
            PollKind.QUESTIONNAIRE -> {
                questionnaires.findById(id).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Questionnaire not found")
                }
                purviews.zipcodesOf(purviews.purviewOf(PollKind.QUESTIONNAIRE, id))
            }
            PollKind.ELECTION -> listOfNotNull(elections.findById(id).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Election not found")
            }.zipcode)
            PollKind.BALLOT_MEASURE -> listOfNotNull(ballotMeasures.findById(id).orElseThrow {
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

    /** Is any of this poll's blocks inside [mine] (the caller's purview)? */
    private fun isBlockedFor(kind: PollKind, pollId: Long, mine: List<org.kodewerks.pollsystem.geography.Region>): Boolean =
        blocks.findByPollTypeAndPollId(kind, pollId).any { b ->
            val area = regions.ofBlock(b) ?: return@any false
            mine.any { it.overlaps(area) }
        }

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
