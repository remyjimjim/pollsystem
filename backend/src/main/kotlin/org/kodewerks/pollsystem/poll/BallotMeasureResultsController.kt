package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.repository.BallotMeasureRepository
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

data class BallotMeasureResultsDto(
    val measureId: Long,
    val title: String,
    /** Raw counts: supers only (null for everyone else). */
    val totalRespondents: Int?,
    val yes: Int?,
    val no: Int?,
    /** Rounded percentages, for everyone. */
    val yesPct: Int,
    val noPct: Int,
    val filterApplied: Map<String, String>?,
    val suppressed: Boolean,
    val suppressionMessage: String?,
    /** The poll's purview, so the page can say where the poll is for. */
    val purviewLevel: ScopeLevel = ScopeLevel.NATIONAL,
    /** Its places (states, "County (ST)" or zipcodes); empty when nationwide. */
    val purviewAreas: List<String> = emptyList(),
    val visibility: ResultsVisibility,
    /** Public comments on this item, anonymous; same privacy steps as the numbers. */
    val comments: List<String> = emptyList()
)

@RestController
@RequestMapping("/api/polls/ballot-measures/{id}/results")
class BallotMeasureResultsController(
    private val measures: BallotMeasureRepository,
    private val blocks: PollBlockService,
    private val purviews: PollPurviewService,
    private val access: ResultsAccess,
    private val publicComments: PublicComments
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
    ): BallotMeasureResultsDto {
        val measure = measures.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Ballot measure not found")
        }
        if (blocks.isBlockedEverywhere(PollKind.BALLOT_MEASURE, measure.id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Ballot measure not found")
        }
        // Ballot measures inherit their election's purview (purviewOf resolves it).
        val purviewRows = purviews.purviewOf(PollKind.BALLOT_MEASURE, id)
        val r = access.prepare(principal, PollKind.BALLOT_MEASURE, id, zipcodes, stateIds, countyIds, withinPurview, outsidePurview)

        val yes = r.answers.count { it.response == "true" }
        val no = r.answers.size - yes
        return BallotMeasureResultsDto(
            measureId = id,
            title = measure.title,
            totalRespondents = if (r.live) r.respondents else null,
            yes = if (r.live) yes else null,
            no = if (r.live) no else null,
            yesPct = pct(yes, yes + no),
            noPct = pct(no, yes + no),
            filterApplied = r.filterApplied,
            suppressed = r.suppressed,
            suppressionMessage = if (r.suppressed) SUPPRESSED else null,
            purviewLevel = purviews.scopeLevelOf(purviewRows),
            purviewAreas = purviews.areaNames(purviewRows),
            visibility = r.visibility,
            comments = publicComments.byItem(PollKind.BALLOT_MEASURE, id, r.answers)[id].orEmpty()
        )
    }
}
