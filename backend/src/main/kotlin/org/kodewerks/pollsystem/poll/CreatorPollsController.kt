package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.BlockScope
import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.repository.BallotMeasureRepository
import org.kodewerks.pollsystem.repository.ElectionRepository
import org.kodewerks.pollsystem.repository.PollTypeBlockRepository
import org.kodewerks.pollsystem.repository.QuestionnaireRepository
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

data class CreatorPollSummary(
    val id: Long,
    val type: String,
    val title: String,
    val status: PollStatus,
    val closeDate: Instant?,
    val createdAt: Instant,
    /** People who have answered (0 for drafts). */
    val respondents: Int = 0,
    /** How many of them live inside the poll's purview; null = withheld (small group). */
    val inArea: Int? = 0,
    /** Milestone emails on for this poll (the creator can pause them). */
    val emails: Boolean = true,
    /** A ballot measure's election (null for other types). */
    val electionId: Long? = null,
    val electionTitle: String? = null,
    /**
     * Whether an admin has switched the poll off: YES (not at all), PARTLY
     * (in some states, counties or zipcodes) or NO (everywhere).
     */
    val enabled: String = "YES",
    /** The poll's area: zipcodes, counties, states or "Nationwide" (2026-10-28). */
    val purview: String = ""
)

/** Pause (false) or resume (true) a poll's milestone emails. */
data class SetEmailsRequest(val enabled: Boolean)

@RestController
@RequestMapping("/api/creator/polls")
class CreatorPollsController(
    private val questionnaires: QuestionnaireRepository,
    private val elections: ElectionRepository,
    private val ballotMeasures: BallotMeasureRepository,
    private val participation: PollParticipationService,
    private val reports: PollReportService,
    private val mutes: org.kodewerks.pollsystem.repository.PollEmailMuteRepository,
    private val history: PollHistoryService,
    private val blocks: PollTypeBlockRepository,
    private val pollPurviews: org.kodewerks.pollsystem.repository.PollPurviewRepository,
    private val purviews: PollPurviewService
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: AppUserDetails,
        @RequestParam(name = "showArchived", required = false, defaultValue = "false") showArchived: Boolean
    ): List<CreatorPollSummary> {
        val id = principal.user.id
        val q = questionnaires.findByCreatorId(id).map {
            CreatorPollSummary(
                it.id, "Questionnaire", it.title, it.status,
                it.closeDate, it.submitDate ?: Instant.now()
            )
        }
        val e = elections.findByCreatorId(id).map {
            CreatorPollSummary(
                it.id, "Election", it.title, it.status,
                it.closeDate, it.dateSubmitted
            )
        }
        // BallotMeasure repo lacks a findByCreatorId — derive from elections for now
        val b = ballotMeasures.findAll()
            .filter { it.creator.id == id }
            .map {
                CreatorPollSummary(
                    it.id, "BallotMeasure", it.title, it.status,
                    it.closeDate, it.dateCreated,
                    electionId = it.election.id, electionTitle = it.election.title
                )
            }
        val all = (q + e + b).sortedByDescending { it.createdAt }
        val listed = if (showArchived) all else all.filterNot { it.status == PollStatus.ARCHIVED }
        // Drafts have no responses; count the rest (a creator has few polls).
        val muted = mutes.findAll().map { it.pollType to it.pollId }.toSet()
        // Areas for the Purview column: one query for all purviews, and a label
        // per distinct area (a super's hundreds of midterm polls share ~50 states).
        val areaRows = pollPurviews.findAll().groupBy { it.pollType to it.pollId }
        val labels = HashMap<List<Any?>, String>()
        fun areaOf(kind: PollKind, pollId: Long): String {
            val rows = areaRows[kind to pollId].orEmpty()
            return labels.getOrPut(rows.map { listOf(it.scopeLevel, it.stateId, it.countyId, it.zipcode) }.sortedBy { it.toString() }) {
                purviews.regionLabel(rows)
            }
        }
        // Loaded once, not per poll: a super's dashboard lists every midterm poll
        // (624), and per-poll queries took 19 s on staging (2026-10-28). Only
        // polls with respondents need the per-poll in-area count.
        val blocksByPoll = blocks.findAll().groupBy { it.pollType to it.pollId }
        val respondentCounts = participation.respondentCounts()
        return listed.map { p ->
            val switchedOff = blocksByPoll[kindOf(p.type) to p.id].orEmpty()
            val enabled = when {
                switchedOff.isEmpty() -> "YES"
                switchedOff.any { it.scope == BlockScope.EVERYWHERE } -> "NO"
                else -> "PARTLY"
            }
            val withEmails = p.copy(emails = (kindOf(p.type) to p.id) !in muted, enabled = enabled,
                purview = areaOf(kindOf(p.type), p.id))
            if (p.status == PollStatus.DRAFT || (respondentCounts[kindOf(p.type) to p.id] ?: 0) == 0) withEmails
            else participation.of(kindOf(p.type), p.id).let { withEmails.copy(respondents = it.respondents, inArea = it.inArea) }
        }
    }

    /**
     * Soft-deletes the creator's own poll by setting status=ARCHIVED. Archived
     * polls disappear from the dashboard listing and from search/voting (every
     * voter-facing query filters on PUBLISHED), but rows and any cast responses
     * are preserved so the deletion is recoverable by a Super if needed.
     */
    @DeleteMapping("/{type}/{id}")
    @Transactional
    fun delete(
        @AuthenticationPrincipal principal: AppUserDetails,
        @PathVariable type: String,
        @PathVariable id: Long
    ) {
        val callerId = principal.user.id
        when (type) {
            "questionnaire" -> {
                val q = questionnaires.findById(id).orElseThrow { notFound() }
                if (q.creator.id != callerId) throw forbidden()
                questionnaires.save(q.copy(status = PollStatus.ARCHIVED, creatorEditedAt = Instant.now()))
                history.record(PollKind.QUESTIONNAIRE, id, org.kodewerks.pollsystem.model.PollEventType.ARCHIVED, principal.user)
            }
            "election" -> {
                val e = elections.findById(id).orElseThrow { notFound() }
                if (e.creator.id != callerId) throw forbidden()
                elections.save(e.copy(status = PollStatus.ARCHIVED, creatorEditedAt = Instant.now()))
                history.record(PollKind.ELECTION, id, org.kodewerks.pollsystem.model.PollEventType.ARCHIVED, principal.user)
            }
            "ballot-measure" -> {
                val bm = ballotMeasures.findById(id).orElseThrow { notFound() }
                if (bm.creator.id != callerId) throw forbidden()
                ballotMeasures.save(bm.copy(status = PollStatus.ARCHIVED, creatorEditedAt = Instant.now()))
                history.record(PollKind.BALLOT_MEASURE, id, org.kodewerks.pollsystem.model.PollEventType.ARCHIVED, principal.user)
            }
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown poll type: $type")
        }
    }

    /**
     * Restores an archived poll back to DRAFT so the creator can review and
     * re-publish. We don't remember the pre-archive status, so DRAFT is the
     * safest landing — the creator hits Publish again if they want it live.
     */
    @PostMapping("/{type}/{id}/restore")
    @Transactional
    fun restore(
        @AuthenticationPrincipal principal: AppUserDetails,
        @PathVariable type: String,
        @PathVariable id: Long
    ) {
        val callerId = principal.user.id
        when (type) {
            "questionnaire" -> {
                val q = questionnaires.findById(id).orElseThrow { notFound() }
                if (q.creator.id != callerId) throw forbidden()
                if (q.status != PollStatus.ARCHIVED) return
                questionnaires.save(q.copy(status = PollStatus.DRAFT, creatorEditedAt = Instant.now()))
                history.record(PollKind.QUESTIONNAIRE, id, org.kodewerks.pollsystem.model.PollEventType.RESTORED, principal.user)
            }
            "election" -> {
                val e = elections.findById(id).orElseThrow { notFound() }
                if (e.creator.id != callerId) throw forbidden()
                if (e.status != PollStatus.ARCHIVED) return
                elections.save(e.copy(status = PollStatus.DRAFT, creatorEditedAt = Instant.now()))
                history.record(PollKind.ELECTION, id, org.kodewerks.pollsystem.model.PollEventType.RESTORED, principal.user)
            }
            "ballot-measure" -> {
                val bm = ballotMeasures.findById(id).orElseThrow { notFound() }
                if (bm.creator.id != callerId) throw forbidden()
                if (bm.status != PollStatus.ARCHIVED) return
                ballotMeasures.save(bm.copy(status = PollStatus.DRAFT, creatorEditedAt = Instant.now()))
                history.record(PollKind.BALLOT_MEASURE, id, org.kodewerks.pollsystem.model.PollEventType.RESTORED, principal.user)
            }
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown poll type: $type")
        }
    }

    /**
     * Reopen a published (or closed) poll for revising (decided 2026-10-17):
     * back to DRAFT, out of search and voting until published again. Responses
     * are kept; the usual edit rules apply (questions, candidates and area are
     * locked once there are responses). Replaces archive + restore for revising.
     */
    @PostMapping("/{type}/{id}/reopen")
    @Transactional
    fun reopen(
        @AuthenticationPrincipal principal: AppUserDetails,
        @PathVariable type: String,
        @PathVariable id: Long
    ) {
        val callerId = principal.user.id
        val reopenable = setOf(PollStatus.PUBLISHED, PollStatus.CLOSED)
        fun notReopenable(): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, "Only a published or closed poll can be reopened")
        val kind = when (type) {
            "questionnaire" -> {
                val q = questionnaires.findById(id).orElseThrow { notFound() }
                if (q.creator.id != callerId) throw forbidden()
                if (q.status !in reopenable) notReopenable()
                questionnaires.save(q.copy(status = PollStatus.DRAFT, creatorEditedAt = Instant.now()))
                PollKind.QUESTIONNAIRE
            }
            "election" -> {
                val e = elections.findById(id).orElseThrow { notFound() }
                if (e.creator.id != callerId) throw forbidden()
                if (e.status !in reopenable) notReopenable()
                elections.save(e.copy(status = PollStatus.DRAFT, creatorEditedAt = Instant.now()))
                PollKind.ELECTION
            }
            "ballot-measure" -> {
                val bm = ballotMeasures.findById(id).orElseThrow { notFound() }
                if (bm.creator.id != callerId) throw forbidden()
                if (bm.status !in reopenable) notReopenable()
                ballotMeasures.save(bm.copy(status = PollStatus.DRAFT, creatorEditedAt = Instant.now()))
                PollKind.BALLOT_MEASURE
            }
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown poll type: $type")
        }
        history.record(kind, id, org.kodewerks.pollsystem.model.PollEventType.REOPENED, principal.user)
    }

    /**
     * Creator reports, phase 2: one poll's report (participation over time,
     * reach, engagement) for its creator, or a super. Privacy rules live in
     * PollReportService. Drafts have no responses, so their report is empty.
     */
    @GetMapping("/{type}/{id}/report")
    @Transactional(readOnly = true)
    fun report(
        @AuthenticationPrincipal principal: AppUserDetails,
        @PathVariable type: String,
        @PathVariable id: Long
    ): PollReport {
        val (kind, creatorId) = when (type) {
            "questionnaire" -> PollKind.QUESTIONNAIRE to questionnaires.findById(id).orElseThrow { notFound() }.creator.id
            "election" -> PollKind.ELECTION to elections.findById(id).orElseThrow { notFound() }.creator.id
            "ballot-measure" -> PollKind.BALLOT_MEASURE to ballotMeasures.findById(id).orElseThrow { notFound() }.creator.id
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown poll type: $type")
        }
        if (creatorId != principal.user.id && principal.user.access != AccessLevel.SUPER) throw forbidden()
        return reports.report(kind, id)
    }

    /** The Creator dashboard's Emails checkbox: pause or resume this poll's milestone emails (owner only). */
    @PutMapping("/{type}/{id}/emails")
    @Transactional
    fun setEmails(
        @AuthenticationPrincipal principal: AppUserDetails,
        @PathVariable type: String,
        @PathVariable id: Long,
        @RequestBody body: SetEmailsRequest
    ): SetEmailsRequest {
        val (kind, creatorId) = when (type) {
            "questionnaire" -> PollKind.QUESTIONNAIRE to questionnaires.findById(id).orElseThrow { notFound() }.creator.id
            "election" -> PollKind.ELECTION to elections.findById(id).orElseThrow { notFound() }.creator.id
            "ballot-measure" -> PollKind.BALLOT_MEASURE to ballotMeasures.findById(id).orElseThrow { notFound() }.creator.id
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown poll type: $type")
        }
        if (creatorId != principal.user.id) throw forbidden()
        val existing = mutes.findByPollTypeAndPollId(kind, id)
        if (body.enabled && existing != null) mutes.delete(existing)
        if (!body.enabled && existing == null) mutes.save(org.kodewerks.pollsystem.model.PollEmailMute(pollType = kind, pollId = id))
        return SetEmailsRequest(enabled = body.enabled)
    }

    private fun notFound() = ResponseStatusException(HttpStatus.NOT_FOUND, "Poll not found")
    private fun forbidden() = ResponseStatusException(HttpStatus.FORBIDDEN, "Not your poll")

    private fun kindOf(type: String): PollKind = when (type) {
        "Questionnaire" -> PollKind.QUESTIONNAIRE
        "Election" -> PollKind.ELECTION
        else -> PollKind.BALLOT_MEASURE
    }
}
