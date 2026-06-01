package com.pollsystem.poll

import com.pollsystem.model.PollKind
import com.pollsystem.repository.BallotMeasureRepository
import com.pollsystem.repository.BallotResponseRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
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
    val totalRespondents: Int,
    val yes: Int,
    val no: Int,
    val filterApplied: Map<String, String>?,
    val suppressed: Boolean,
    val suppressionMessage: String?
)

@RestController
@RequestMapping("/api/polls/ballot-measures/{id}/results")
class BallotMeasureResultsController(
    private val measures: BallotMeasureRepository,
    private val responses: BallotResponseRepository,
    private val blocks: PollBlockService,
    @Value("\${app.results.k-anonymity-threshold:10}") private val kThreshold: Int
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun get(
        @PathVariable id: Long,
        @RequestParam(required = false) zipcode: String?,
        @RequestParam(required = false, defaultValue = "false") onlyPurview: Boolean = false
    ): BallotMeasureResultsDto {
        val measure = measures.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Ballot measure not found")
        }
        if (blocks.isBlocked(PollKind.BALLOT_MEASURE, measure.id)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Ballot measure not found")
        }
        val purviewZips = setOf(measure.election.zipcode)
        val all = responses.findByMeasureId(id)
        var filtered = all
        if (zipcode != null) filtered = filtered.filter { it.user.zipcode == zipcode }
        if (onlyPurview) filtered = filtered.filter { it.user.zipcode in purviewZips }

        val respondents = filtered.size  // unique by (user, measure) constraint
        val filterMap = buildMap {
            zipcode?.let { put("zipcode", it) }
            if (onlyPurview) put("onlyPurview", "true")
        }.takeIf { it.isNotEmpty() }

        if ((zipcode != null || onlyPurview) && respondents < kThreshold) {
            return BallotMeasureResultsDto(
                measureId = id,
                title = measure.title,
                totalRespondents = 0,
                yes = 0,
                no = 0,
                filterApplied = filterMap,
                suppressed = true,
                suppressionMessage = "Not enough responses in this group to display (privacy protection)"
            )
        }

        return BallotMeasureResultsDto(
            measureId = id,
            title = measure.title,
            totalRespondents = respondents,
            yes = filtered.count { it.response },
            no = filtered.count { !it.response },
            filterApplied = filterMap,
            suppressed = false,
            suppressionMessage = null
        )
    }
}
