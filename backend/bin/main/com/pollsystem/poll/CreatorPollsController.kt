package com.pollsystem.poll

import com.pollsystem.model.PollStatus
import com.pollsystem.repository.BallotMeasureRepository
import com.pollsystem.repository.ElectionRepository
import com.pollsystem.repository.QuestionnaireRepository
import com.pollsystem.security.AppUserDetails
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class CreatorPollSummary(
    val id: Long,
    val type: String,
    val title: String,
    val status: PollStatus,
    val closeDate: Instant?,
    val createdAt: Instant
)

@RestController
@RequestMapping("/api/creator/polls")
class CreatorPollsController(
    private val questionnaires: QuestionnaireRepository,
    private val elections: ElectionRepository,
    private val ballotMeasures: BallotMeasureRepository
) {
    @GetMapping
    fun list(@AuthenticationPrincipal principal: AppUserDetails): List<CreatorPollSummary> {
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
                    it.closeDate, it.dateCreated
                )
            }
        return (q + e + b).sortedByDescending { it.createdAt }
    }
}
