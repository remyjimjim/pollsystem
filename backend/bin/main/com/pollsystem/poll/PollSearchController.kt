package com.pollsystem.poll

import com.pollsystem.repository.BallotMeasureRepository
import com.pollsystem.repository.CandidateRepository
import com.pollsystem.repository.ElectionRepository
import com.pollsystem.repository.QuestionnaireDomainRepository
import com.pollsystem.repository.QuestionnaireRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class PollSearchResult(
    val id: Long,
    val type: String,
    val title: String,
    val creatorEmail: String,
    val closeDate: Instant?,
    val zipcodes: List<String>
)

@RestController
@RequestMapping("/api/polls/search")
class PollSearchController(
    private val questionnaires: QuestionnaireRepository,
    private val domains: QuestionnaireDomainRepository,
    private val elections: ElectionRepository,
    private val ballotMeasures: BallotMeasureRepository,
    private val candidates: CandidateRepository
) {

    @GetMapping
    fun search(
        @RequestParam(required = false) title: String?,
        @RequestParam(required = false) zipcode: String?,
        @RequestParam(required = false) creatorEmail: String?,
        @RequestParam(required = false) candidateName: String?,
        @RequestParam(required = false) type: String?
    ): List<PollSearchResult> {
        val now = Instant.now()
        val results = mutableListOf<PollSearchResult>()

        val electionsWithCandidate: Set<Long>? = if (!candidateName.isNullOrBlank()) {
            candidates.findByNameContainingIgnoreCase(candidateName)
                .map { it.election.id }
                .toSet()
        } else null

        // Candidate-name filter only matches Elections; skip the other types entirely.
        if (electionsWithCandidate == null &&
            (type == null || type.equals("Questionnaire", ignoreCase = true))
        ) {
            for (q in questionnaires.findActive(now)) {
                if (!matches(q.title, title)) continue
                if (!matches(q.creator.email, creatorEmail)) continue
                val zips = domains.findByQuestionnaireId(q.id).map { it.zipcode }.distinct().sorted()
                if (zipcode != null && zipcode !in zips) continue
                results += PollSearchResult(
                    id = q.id,
                    type = "Questionnaire",
                    title = q.title,
                    creatorEmail = q.creator.email,
                    closeDate = q.closeDate,
                    zipcodes = zips
                )
            }
        }

        if (type == null || type.equals("Election", ignoreCase = true)) {
            for (e in elections.findActive(now)) {
                if (electionsWithCandidate != null && e.id !in electionsWithCandidate) continue
                if (!matches(e.title, title)) continue
                if (!matches(e.creator.email, creatorEmail)) continue
                if (zipcode != null && zipcode != e.zipcode) continue
                results += PollSearchResult(
                    id = e.id,
                    type = "Election",
                    title = e.title,
                    creatorEmail = e.creator.email,
                    closeDate = e.closeDate,
                    zipcodes = listOf(e.zipcode)
                )
            }
        }

        if (electionsWithCandidate == null &&
            (type == null || type.equals("BallotMeasure", ignoreCase = true))
        ) {
            for (bm in ballotMeasures.findActive(now)) {
                if (!matches(bm.title, title)) continue
                if (!matches(bm.creator.email, creatorEmail)) continue
                val zip = bm.election.zipcode
                if (zipcode != null && zipcode != zip) continue
                results += PollSearchResult(
                    id = bm.id,
                    type = "BallotMeasure",
                    title = bm.title,
                    creatorEmail = bm.creator.email,
                    closeDate = bm.closeDate,
                    zipcodes = listOf(zip)
                )
            }
        }

        return results.sortedWith(
            compareBy({ it.closeDate ?: Instant.MAX }, { it.title })
        )
    }

    private fun matches(field: String, query: String?): Boolean =
        query.isNullOrBlank() || field.contains(query, ignoreCase = true)
}
