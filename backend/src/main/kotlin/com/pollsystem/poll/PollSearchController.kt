package com.pollsystem.poll

import com.pollsystem.repository.BallotMeasureRepository
import com.pollsystem.repository.CandidateRepository
import com.pollsystem.repository.CountyZipsRepository
import com.pollsystem.repository.ElectionRepository
import com.pollsystem.repository.QuestionnaireDomainRepository
import com.pollsystem.repository.QuestionnaireRepository
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
    val zipcodes: List<ZipState>
)

/** Distinct values that feed the autocomplete datalists on the search form. */
data class SearchSuggestions(
    val titles: List<String>,
    val candidates: List<String>,
    val zipcodes: List<String>
)

@RestController
@RequestMapping("/api/polls/search")
class PollSearchController(
    private val questionnaires: QuestionnaireRepository,
    private val domains: QuestionnaireDomainRepository,
    private val elections: ElectionRepository,
    private val ballotMeasures: BallotMeasureRepository,
    private val candidates: CandidateRepository,
    private val countyZips: CountyZipsRepository
) {

    @GetMapping
    fun search(
        @RequestParam(required = false) title: String?,
        @RequestParam(required = false) zipcode: String?,
        @RequestParam(required = false) creatorEmail: String?,
        @RequestParam(required = false) candidateName: String?,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false, defaultValue = "false") includeClosed: Boolean
    ): List<PollSearchResult> {
        val now = Instant.now()
        val results = mutableListOf<PollSearchResult>()

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
                val zipStates = domains.findByQuestionnaireId(q.id)
                    .map { ZipState(it.zipcode, it.state.initial) }
                    .distinctBy { it.code }
                    .sortedBy { it.code }
                if (zipcode != null && zipStates.none { it.code == zipcode }) continue
                results += PollSearchResult(
                    id = q.id,
                    type = "Questionnaire",
                    title = q.title,
                    creatorEmail = q.creator.email,
                    closeDate = q.closeDate,
                    zipcodes = zipStates
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
                if (zipcode != null && zipcode != e.zipcode) continue
                results += PollSearchResult(
                    id = e.id,
                    type = "Election",
                    title = e.title,
                    creatorEmail = e.creator.email,
                    closeDate = e.closeDate,
                    zipcodes = listOf(ZipState(e.zipcode, lookupState(e.zipcode)))
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
                if (zipcode != null && zipcode != zip) continue
                results += PollSearchResult(
                    id = bm.id,
                    type = "BallotMeasure",
                    title = bm.title,
                    creatorEmail = bm.creator.email,
                    closeDate = bm.closeDate,
                    zipcodes = listOf(ZipState(zip, lookupState(zip)))
                )
            }
        }

        // Active polls (no closeDate or future) first, sorted by closeDate
        // ascending; closed polls last, sorted by closeDate descending so the
        // most-recently-closed appears at the top of the closed section.
        return results.sortedWith(
            compareBy<PollSearchResult>(
                { if (it.closeDate != null && !it.closeDate.isAfter(now)) 1 else 0 },
                { if (it.closeDate != null && !it.closeDate.isAfter(now)) -it.closeDate.toEpochMilli() else (it.closeDate?.toEpochMilli() ?: Long.MAX_VALUE) },
                { it.title }
            )
        )
    }

    /**
     * Feeds the autocomplete datalists on the search form: distinct titles
     * and candidate names drawn only from currently-active polls.
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

        val candidateNames = activeElections
            .flatMap { candidates.findByElectionId(it.id) }
            .map { it.name }
            .distinct()
            .sorted()

        val zipcodes = (
            activeQuestionnaires.flatMap { domains.findByQuestionnaireId(it.id).map { d -> d.zipcode } } +
                activeElections.map { it.zipcode } +
                activeBallotMeasures.map { it.election.zipcode }
            ).distinct().sorted()

        return SearchSuggestions(titles, candidateNames, zipcodes)
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
