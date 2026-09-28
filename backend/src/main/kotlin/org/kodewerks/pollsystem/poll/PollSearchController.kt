package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.repository.BallotMeasureRepository
import org.kodewerks.pollsystem.repository.CandidateRepository
import org.kodewerks.pollsystem.repository.CountyRepository
import org.kodewerks.pollsystem.repository.CountyZipsRepository
import org.kodewerks.pollsystem.repository.ElectionRepository
import org.kodewerks.pollsystem.repository.PollPurviewRepository
import org.kodewerks.pollsystem.repository.QuestionnaireRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/** A zipcode paired with its 2-letter state initial. */
data class ZipState(val code: String, val state: String)

data class PollSearchResult(
    val id: Long,
    val type: String,
    val title: String,
    val creatorEmail: String,
    val closeDate: Instant?,
    val zipcodes: List<ZipState>,
    /** Human-readable purview (e.g. "California", "Nationwide"); shown when a poll has no single zip. */
    val regionLabel: String? = null
)

/** Distinct values that feed the autocomplete datalists on the search form. */
data class SearchSuggestions(
    val titles: List<String>,
    val candidates: List<String>
)

@RestController
@RequestMapping("/api/polls/search")
class PollSearchController(
    private val questionnaires: QuestionnaireRepository,
    private val elections: ElectionRepository,
    private val ballotMeasures: BallotMeasureRepository,
    private val candidates: CandidateRepository,
    private val countyZips: CountyZipsRepository,
    private val counties: CountyRepository,
    private val pollPurviews: PollPurviewRepository,
    private val purviewService: PollPurviewService,
    private val blockService: PollBlockService
) {

    /** Maps the search-row `type` string back to the block-table enum. */
    private fun kindOf(typeName: String): PollKind = when (typeName) {
        "Questionnaire" -> PollKind.QUESTIONNAIRE
        "Election" -> PollKind.ELECTION
        "BallotMeasure" -> PollKind.BALLOT_MEASURE
        else -> error("Unknown poll type $typeName")
    }

    @GetMapping
    fun search(
        @RequestParam(required = false) title: String?,
        @RequestParam(name = "zipcode", required = false) zipcodes: List<String>?,
        @RequestParam(name = "countyId", required = false) countyIds: List<Long>?,
        @RequestParam(name = "stateId", required = false) stateIds: List<Long>?,
        @RequestParam(required = false) creatorEmail: String?,
        @RequestParam(required = false) candidateName: String?,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false, defaultValue = "false") includeClosed: Boolean
    ): List<PollSearchResult> {
        val now = Instant.now()
        val results = mutableListOf<PollSearchResult>()

        // Geo filter resolves to a set of acceptable zipcodes:
        // - one or more zipcode picks → those (the user's explicit
        //   subset).
        // - county only → every zip in that county.
        // - state only → every zip in every county of that state
        //   (the "Any zipcode under this state" case).
        // - none → no geo filter.
        val pickedZips = zipcodes?.filter { it.isNotBlank() }
        val geoFilter: Set<String>? = when {
            !pickedZips.isNullOrEmpty() -> pickedZips.toSet()
            !countyIds.isNullOrEmpty() -> countyZips.findByCountyIdIn(countyIds)
                .map { it.zipcode }
                .toSet()
            !stateIds.isNullOrEmpty() -> {
                val countyIdsInStates = counties.findByStateIdIn(stateIds).map { it.id }
                if (countyIdsInStates.isEmpty()) emptySet()
                else countyZips.findByCountyIdIn(countyIdsInStates).map { it.zipcode }.toSet()
            }
            else -> null
        }

        // Purview-aware geo match: a poll surfaces for a geo search when the
        // SEARCHED location falls inside the poll's purview (zip ∈ county ∈ state
        // ∈ nation), read from poll_purviews — not when the poll's own stored zip
        // happens to equal the searched one. Resolve the searched zips to their
        // counties/states once; load purview rows only when a geo filter is set.
        val searchedZips: Set<String> = geoFilter ?: emptySet()
        val searchedMeta = if (searchedZips.isEmpty()) emptyList()
            else countyZips.findByZipcodeIn(searchedZips.toList())
        val searchedCounties = searchedMeta.map { it.county.id }.toSet()
        val searchedStates = searchedMeta.map { it.county.state.id }.toSet()
        val purviewByPoll = if (geoFilter == null) emptyMap()
            else pollPurviews.findAll().groupBy { it.pollType to it.pollId }
        fun purviewIncludesSearch(kind: PollKind, pollId: Long): Boolean {
            val rows = purviewByPoll[kind to pollId].orEmpty()
            if (rows.isEmpty()) return true // no declared purview = nationwide
            return rows.any { r ->
                when (r.scopeLevel) {
                    ScopeLevel.NATIONAL -> true
                    ScopeLevel.ZIP -> r.zipcode in searchedZips
                    ScopeLevel.COUNTY -> r.countyId in searchedCounties
                    ScopeLevel.STATE -> r.stateId in searchedStates
                }
            }
        }

        val titleQuery = title?.takeIf { it.isNotBlank() }
        val candidateQuery = candidateName?.takeIf { it.isNotBlank() }

        // Elections whose candidate roster matches the candidate-name filter.
        val electionsWithCandidate: Set<Long> = if (candidateQuery != null) {
            candidates.findByNameContainingIgnoreCase(candidateQuery)
                .map { it.election.id }
                .toSet()
        } else emptySet()

        /**
         * Title and candidate name are OR-combined: a poll matches if either
         * hits. A blank field drops out of the OR rather than matching all.
         * (Type and zipcode are applied separately as AND constraints.)
         */
        fun textMatch(titleHit: Boolean, candidateHit: Boolean): Boolean = when {
            titleQuery == null && candidateQuery == null -> true
            titleQuery != null && candidateQuery != null -> titleHit || candidateHit
            titleQuery != null -> titleHit
            else -> candidateHit
        }

        if (type == null || type.equals("Questionnaire", ignoreCase = true)) {
            val pool = questionnaires.findActive(now) +
                if (includeClosed) questionnaires.findExpiredQuestionnaires(now) else emptyList()
            for (q in pool) {
                // Questionnaires have no candidates, so only the title can hit.
                if (!textMatch(titleHit = titleHit(q.title, titleQuery), candidateHit = false)) continue
                if (!matches(q.creator.email, creatorEmail)) continue
                if (geoFilter != null && !purviewIncludesSearch(PollKind.QUESTIONNAIRE, q.id)) continue
                val qRows = purviewService.purviewOf(PollKind.QUESTIONNAIRE, q.id)
                val zipStates = purviewService.zipcodesOf(qRows)
                    .map { ZipState(it, lookupState(it)) }
                    .sortedBy { it.code }
                results += PollSearchResult(
                    id = q.id,
                    type = "Questionnaire",
                    title = q.title,
                    creatorEmail = q.creator.email,
                    closeDate = q.closeDate,
                    zipcodes = zipStates,
                    regionLabel = purviewService.regionLabel(qRows)
                )
            }
        }

        if (type == null || type.equals("Election", ignoreCase = true)) {
            val pool = elections.findActive(now) +
                if (includeClosed) elections.findExpiredElections(now) else emptyList()
            for (e in pool) {
                if (!textMatch(
                        titleHit = titleHit(e.title, titleQuery),
                        candidateHit = e.id in electionsWithCandidate
                    )
                ) continue
                if (!matches(e.creator.email, creatorEmail)) continue
                if (geoFilter != null && !purviewIncludesSearch(PollKind.ELECTION, e.id)) continue
                results += PollSearchResult(
                    id = e.id,
                    type = "Election",
                    title = e.title,
                    creatorEmail = e.creator.email,
                    closeDate = e.closeDate,
                    zipcodes = listOfNotNull(e.zipcode?.let { ZipState(it, lookupState(it)) }),
                    regionLabel = purviewService.regionLabel(purviewService.purviewOf(PollKind.ELECTION, e.id))
                )
            }
        }

        if (type == null || type.equals("BallotMeasure", ignoreCase = true)) {
            val pool = ballotMeasures.findActive(now) +
                if (includeClosed) ballotMeasures.findExpiredBallotMeasures(now) else emptyList()
            for (bm in pool) {
                // Ballot measures have no candidates, so only the title can hit.
                if (!textMatch(titleHit = titleHit(bm.title, titleQuery), candidateHit = false)) continue
                if (!matches(bm.creator.email, creatorEmail)) continue
                val zip = bm.election.zipcode
                // Ballot measures inherit their election's purview.
                if (geoFilter != null && !purviewIncludesSearch(PollKind.ELECTION, bm.election.id)) continue
                results += PollSearchResult(
                    id = bm.id,
                    type = "BallotMeasure",
                    title = bm.title,
                    creatorEmail = bm.creator.email,
                    closeDate = bm.closeDate,
                    zipcodes = listOfNotNull(zip?.let { ZipState(it, lookupState(it)) }),
                    regionLabel = purviewService.regionLabel(purviewService.purviewOf(PollKind.ELECTION, bm.election.id))
                )
            }
        }

        // Hide polls that admins have blocked individually.
        val visible = blockService.filterUnblocked(
            results,
            type = { kindOf(it.type) },
            id = { it.id }
        )

        // Active polls (no closeDate or future) first, sorted by closeDate
        // ascending; closed polls last, sorted by closeDate descending so the
        // most-recently-closed appears at the top of the closed section.
        return visible.sortedWith(
            compareBy<PollSearchResult>(
                { if (it.closeDate != null && !it.closeDate.isAfter(now)) 1 else 0 },
                { if (it.closeDate != null && !it.closeDate.isAfter(now)) -it.closeDate.toEpochMilli() else (it.closeDate?.toEpochMilli() ?: Long.MAX_VALUE) },
                { it.title }
            )
        )
    }

    /**
     * Feeds the autocomplete datalists on the search form: distinct titles
     * drawn from currently-active polls, plus candidate names drawn from
     * every election (active or not). Including past elections lets the
     * user retype a name they remember from a closed race without having
     * to know whether that race is still open.
     */
    @GetMapping("/suggestions")
    fun suggestions(): SearchSuggestions {
        val now = Instant.now()
        val activeQuestionnaires = questionnaires.findActive(now)
        val activeElections = elections.findActive(now)
        val activeBallotMeasures = ballotMeasures.findActive(now)

        val titles = (
            activeQuestionnaires.map { it.title } +
                activeElections.map { it.title } +
                activeBallotMeasures.map { it.title }
            ).distinct().sorted()

        val candidateNames = candidates.findAll()
            .map { it.name }
            .distinct()
            .sorted()

        return SearchSuggestions(titles, candidateNames)
    }

    /**
     * Resolves a 5-digit zip to its state initial via the seeded county_zips
     * table. Returns "??" if no match — shouldn't happen for any poll that
     * was created through our forms, but the column is non-null in the DTO.
     */
    private fun lookupState(zip: String): String =
        countyZips.findByZipcode(zip).firstOrNull()?.county?.state?.initial ?: "??"

    private fun matches(field: String, query: String?): Boolean =
        query.isNullOrBlank() || field.contains(query, ignoreCase = true)

    /** A title hit requires a non-blank query; a blank query is not a hit. */
    private fun titleHit(title: String, query: String?): Boolean =
        query != null && title.contains(query, ignoreCase = true)
}
