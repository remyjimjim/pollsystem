package org.kodewerks.pollsystem.superadmin

import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.repository.BallotMeasureRepository
import org.kodewerks.pollsystem.repository.ElectionRepository
import org.kodewerks.pollsystem.repository.QuestionnaireRepository
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/**
 * Super-admin reach across every creator's polls. Reads list the full
 * inventory; the PUT endpoint updates only the fields documented as safe
 * to mutate after publication (title, summary, closeDate). Voter-facing
 * structure — questions, candidates, geo scope — is intentionally out of
 * scope: changing it would silently re-shape responses already cast.
 */
data class SuperPollRow(
    val id: Long,
    val type: String,
    val title: String,
    val summary: String?,
    val status: PollStatus,
    val creatorEmail: String,
    val closeDate: Instant?
)

data class SuperPollEdit(
    val title: String?,
    val summary: String?,
    val closeDate: Instant?
)

@RestController
@RequestMapping("/api/super/polls")
class SuperPollsController(
    private val questionnaires: QuestionnaireRepository,
    private val elections: ElectionRepository,
    private val ballotMeasures: BallotMeasureRepository
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun list(): List<SuperPollRow> {
        val rows = mutableListOf<SuperPollRow>()
        questionnaires.findAll().forEach {
            rows += SuperPollRow(
                it.id, "questionnaire", it.title, it.summary, it.status, it.creator.email, it.closeDate
            )
        }
        elections.findAll().forEach {
            rows += SuperPollRow(
                it.id, "election", it.title, null, it.status, it.creator.email, it.closeDate
            )
        }
        ballotMeasures.findAll().forEach {
            rows += SuperPollRow(
                it.id, "ballot-measure", it.title, it.summary, it.status, it.creator.email, it.closeDate
            )
        }
        return rows.sortedWith(compareBy({ it.type }, { it.title }))
    }

    @PutMapping("/{type}/{id}")
    @Transactional
    fun update(
        @PathVariable type: String,
        @PathVariable id: Long,
        @RequestBody body: SuperPollEdit
    ): SuperPollRow {
        val title = body.title?.trim()
        if (title != null && title.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Title cannot be blank")
        }
        val summary = body.summary?.trim()
        return when (type) {
            "questionnaire" -> {
                val existing = questionnaires.findById(id).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Questionnaire not found")
                }
                val saved = questionnaires.save(
                    existing.copy(
                        title = title ?: existing.title,
                        summary = summary ?: existing.summary,
                        closeDate = body.closeDate
                    )
                )
                SuperPollRow(
                    saved.id, "questionnaire", saved.title, saved.summary,
                    saved.status, saved.creator.email, saved.closeDate
                )
            }
            "election" -> {
                val existing = elections.findById(id).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Election not found")
                }
                val saved = elections.save(
                    existing.copy(
                        title = title ?: existing.title,
                        closeDate = body.closeDate
                    )
                )
                SuperPollRow(
                    saved.id, "election", saved.title, null,
                    saved.status, saved.creator.email, saved.closeDate
                )
            }
            "ballot-measure" -> {
                val existing = ballotMeasures.findById(id).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Ballot measure not found")
                }
                val saved = ballotMeasures.save(
                    existing.copy(
                        title = title ?: existing.title,
                        summary = summary ?: existing.summary,
                        closeDate = body.closeDate
                    )
                )
                SuperPollRow(
                    saved.id, "ballot-measure", saved.title, saved.summary,
                    saved.status, saved.creator.email, saved.closeDate
                )
            }
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown poll type: $type")
        }
    }
}
