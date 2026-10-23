package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.repository.CandidateRepository
import org.kodewerks.pollsystem.repository.ElectionRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

data class CandidateResultDto(
    val candidateId: Long,
    val name: String,
    val affiliation: String,
    val officeName: String,
    /** Raw counts: supers only (null for everyone else). */
    val yes: Int?,
    val no: Int?,
    val total: Int?,
    /** Rounded percentages of this candidate's responses, for everyone. */
    val yesPct: Int,
    val noPct: Int,
    /** Public comments on this item, anonymous; same privacy steps as the numbers. */
    val comments: List<String> = emptyList()
)

data class ElectionResultsDto(
    val electionId: Long,
    val title: String,
    /** Respondents in the shown group: supers only (null for everyone else). */
    val totalRespondents: Int?,
    val perCandidate: List<CandidateResultDto>,
    val filterApplied: Map<String, String>?,
    val suppressed: Boolean,
    val suppressionMessage: String?,
    /** The poll's purview, so the page can say where the poll is for. */
    val purviewLevel: ScopeLevel = ScopeLevel.NATIONAL,
    /** Its places (states, "County (ST)" or zipcodes); empty when nationwide. */
    val purviewAreas: List<String> = emptyList(),
    val visibility: ResultsVisibility,
    /** Respondents inside / outside the purview (null = withheld, small group). */
    val purviewCounts: PurviewCounts = PurviewCounts(null, null),
    /** The rest of the ballot (V37), so its results page can show them all: included elections, and the published measures of this one and those. */
    val includedElectionIds: List<Long> = emptyList(),
    val ballotMeasureIds: List<Long> = emptyList()
)

@RestController
@RequestMapping("/api/polls/elections/{id}/results")
class ElectionResultsController(
    private val elections: ElectionRepository,
    private val candidates: CandidateRepository,
    private val blocks: PollBlockService,
    private val purviews: PollPurviewService,
    private val access: ResultsAccess,
    private val publicComments: PublicComments,
    private val includes: ElectionIncludes,
    private val measures: org.kodewerks.pollsystem.repository.BallotMeasureRepository
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun get(
        @PathVariable id: Long,
        @RequestParam(name = "zipcode", required = false) zipcodes: List<String>? = null,
        @RequestParam(name = "stateId", required = false) stateIds: List<Long>? = null,
        @RequestParam(name = "countyId", required = false) countyIds: List<Long>? = null,
        @RequestParam(required = false, defaultValue = "true") withinPurview: Boolean = true,
        @RequestParam(required = false, defaultValue = "true") outsidePurview: Boolean = true,
        @AuthenticationPrincipal principal: AppUserDetails? = null
    ): ElectionResultsDto {
        val election = elections.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Election not found")
        }
        // An Everywhere block hides the poll from public results too.
        if (blocks.isBlockedEverywhere(PollKind.ELECTION, election.id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Election not found")
        }
        val purviewRows = purviews.purviewOf(PollKind.ELECTION, id)
        val r = access.prepare(principal, PollKind.ELECTION, id, zipcodes, stateIds, countyIds, withinPurview, outsidePurview)

        val comments = publicComments.byItem(PollKind.ELECTION, id, r.answers)
        val perCandidate = if (r.answers.isEmpty() && !r.live) emptyList() else candidates.findByElectionId(id).map { c ->
            val rows = r.answers.filter { it.itemId == c.id }
            val yes = rows.count { it.response == "true" }
            val no = rows.size - yes
            CandidateResultDto(
                candidateId = c.id, name = c.name, affiliation = c.affiliation, officeName = c.office.name,
                yes = if (r.live) yes else null, no = if (r.live) no else null, total = if (r.live) rows.size else null,
                yesPct = pct(yes, rows.size), noPct = pct(no, rows.size),
                comments = comments[c.id].orEmpty()
            )
        }
        return ElectionResultsDto(
            electionId = id,
            title = election.title,
            totalRespondents = if (r.live) r.respondents else null,
            perCandidate = perCandidate,
            filterApplied = r.filterApplied,
            suppressed = r.suppressed,
            suppressionMessage = if (r.suppressed) SUPPRESSED else null,
            purviewLevel = purviews.scopeLevelOf(purviewRows),
            purviewAreas = purviews.areaNames(purviewRows),
            visibility = r.visibility,
            purviewCounts = r.purviewCounts,
            includedElectionIds = includes.of(id),
            ballotMeasureIds = (listOf(id) + includes.of(id)).flatMap { e ->
                measures.findByElectionId(e).filter { it.status != org.kodewerks.pollsystem.model.PollStatus.DRAFT }.map { it.id }
            }
        )
    }
}
