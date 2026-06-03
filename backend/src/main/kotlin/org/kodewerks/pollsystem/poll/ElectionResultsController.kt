package com.pollsystem.poll

import com.pollsystem.model.PollKind
import com.pollsystem.repository.CandidateRepository
import com.pollsystem.repository.CandidateResponseRepository
import com.pollsystem.repository.CountyRepository
import com.pollsystem.repository.CountyZipsRepository
import com.pollsystem.repository.ElectionRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
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
    val yes: Int,
    val no: Int,
    val total: Int
)

data class ElectionResultsDto(
    val electionId: Long,
    val title: String,
    val totalRespondents: Int,
    val perCandidate: List<CandidateResultDto>,
    val filterApplied: Map<String, String>?,
    val suppressed: Boolean,
    val suppressionMessage: String?
)

@RestController
@RequestMapping("/api/polls/elections/{id}/results")
class ElectionResultsController(
    private val elections: ElectionRepository,
    private val candidates: CandidateRepository,
    private val responses: CandidateResponseRepository,
    private val blocks: PollBlockService,
    private val countyZips: CountyZipsRepository,
    private val counties: CountyRepository,
    @Value("\${app.results.k-anonymity-threshold:10}") private val kThreshold: Int
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun get(
        @PathVariable id: Long,
        @RequestParam(name = "zipcode", required = false) zipcodes: List<String>? = null,
        @RequestParam(name = "stateId", required = false) stateIds: List<Long>? = null,
        @RequestParam(name = "countyId", required = false) countyIds: List<Long>? = null,
        @RequestParam(required = false, defaultValue = "false") onlyPurview: Boolean = false
    ): ElectionResultsDto {
        val election = elections.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Election not found")
        }
        // Admin block hides the poll from public results too.
        if (blocks.isBlocked(PollKind.ELECTION, election.id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Election not found")
        }
        val candidateList = candidates.findByElectionId(id)
        val purviewZips = setOf(election.zipcode)
        val geoZips = resolveGeoFilter(zipcodes, stateIds, countyIds, counties, countyZips)
        val all = responses.findByElectionId(id)
        var filtered = all
        if (geoZips != null) filtered = filtered.filter { it.user.zipcode in geoZips }
        if (onlyPurview) filtered = filtered.filter { it.user.zipcode in purviewZips }

        val respondents = filtered.map { it.user.id }.distinct().size
        val filterMap = describeFilter(zipcodes, stateIds, countyIds, onlyPurview)

        if ((geoZips != null || onlyPurview) && respondents < kThreshold) {
            return ElectionResultsDto(
                electionId = id,
                title = election.title,
                totalRespondents = 0,
                perCandidate = emptyList(),
                filterApplied = filterMap,
                suppressed = true,
                suppressionMessage = "Not enough responses in this group to display (privacy protection)"
            )
        }

        val perCandidate = candidateList.map { c ->
            val rows = filtered.filter { it.candidate.id == c.id }
            val yes = rows.count { it.response }
            val no = rows.count { !it.response }
            CandidateResultDto(
                candidateId = c.id,
                name = c.name,
                affiliation = c.affiliation,
                officeName = c.office.name,
                yes = yes,
                no = no,
                total = rows.size
            )
        }

        return ElectionResultsDto(
            electionId = id,
            title = election.title,
            totalRespondents = respondents,
            perCandidate = perCandidate,
            filterApplied = filterMap,
            suppressed = false,
            suppressionMessage = null
        )
    }
}
