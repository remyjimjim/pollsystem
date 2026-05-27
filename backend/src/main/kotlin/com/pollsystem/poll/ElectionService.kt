package com.pollsystem.poll

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.pollsystem.model.Candidate
import com.pollsystem.model.Election
import com.pollsystem.model.Office
import com.pollsystem.model.PollStatus
import com.pollsystem.model.User
import com.pollsystem.repository.CandidateRepository
import com.pollsystem.repository.CandidateResponseRepository
import com.pollsystem.repository.ElectionRepository
import com.pollsystem.repository.OfficeRepository
import com.pollsystem.repository.PollTypeRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class ElectionService(
    private val elections: ElectionRepository,
    private val candidates: CandidateRepository,
    private val candidateResponses: CandidateResponseRepository,
    private val offices: OfficeRepository,
    private val pollTypes: PollTypeRepository,
    private val objectMapper: ObjectMapper
) {

    @Transactional
    fun saveDraft(creator: User, dto: ElectionDraftRequest): Election {
        val pt = pollTypes.findById(dto.pollTypeId).orElseThrow {
            ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown poll type")
        }
        val saved = elections.save(
            Election(
                creator = creator,
                pollType = pt,
                title = dto.title.trim(),
                date = dto.date,
                zipcode = dto.zipcode,
                status = PollStatus.DRAFT,
                closeDate = dto.closeDate
            )
        )
        replaceCandidates(saved, dto.candidates)
        return saved
    }

    @Transactional
    fun update(id: Long, creator: User, dto: ElectionDraftRequest): Election {
        val existing = loadOwned(id, creator)
        if (existing.status != PollStatus.DRAFT) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT can be edited")
        }
        val pt = pollTypes.findById(dto.pollTypeId).orElseThrow {
            ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown poll type")
        }
        val updated = elections.save(
            existing.copy(
                pollType = pt,
                title = dto.title.trim(),
                date = dto.date,
                zipcode = dto.zipcode,
                closeDate = dto.closeDate
            )
        )
        replaceCandidates(updated, dto.candidates)
        return updated
    }

    @Transactional
    fun publish(id: Long, creator: User, confirmed: Boolean): Election {
        val existing = loadOwned(id, creator)
        if (existing.status != PollStatus.DRAFT) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT can be published")
        }
        if (candidates.findByElectionId(id).isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one candidate required")
        }
        validateClose(existing.closeDate, confirmed)
        return elections.save(existing.copy(status = PollStatus.PUBLISHED))
    }

    @Transactional(readOnly = true)
    fun get(id: Long): Election = elections.findById(id).orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND, "Election not found")
    }

    @Transactional(readOnly = true)
    fun toDto(e: Election): ElectionDto {
        val (widget, groupBy) = readCandidatesHints(e.pollType.templateJson)
        return ElectionDto.from(
            e,
            candidates.findByElectionId(e.id),
            candidatesWidget = widget,
            candidatesGroupBy = groupBy
        )
    }

    /**
     * Pulls the candidate rendering hints out of the poll-type template JSON.
     * Returns nulls (legacy per-candidate Yes/No ballot) on any parse trouble.
     */
    private fun readCandidatesHints(templateJson: String): Pair<String?, String?> = try {
        val root: JsonNode = objectMapper.readTree(templateJson)
        val candidates = root.path("fields").path("candidates")
        val widget = candidates.path("widget").asText(null)?.takeIf { it.isNotBlank() }
        val groupBy = candidates.path("groupBy").asText(null)?.takeIf { it.isNotBlank() }
        widget to groupBy
    } catch (_: Exception) {
        null to null
    }

    private fun loadOwned(id: Long, creator: User): Election {
        val e = get(id)
        if (e.creator.id != creator.id) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }
        return e
    }

    private fun replaceCandidates(election: Election, inputs: List<CandidateInput>) {
        val existing = candidates.findByElectionId(election.id)
        val existingTuples = existing.map {
            Triple(it.name.trim(), it.affiliation.trim(), it.office.name.trim())
        }
        val incomingTuples = inputs.map {
            Triple(it.name.trim(), it.affiliation.trim(), it.officeName.trim())
        }
        // No structural change → don't touch the candidate rows. Skipping
        // is what lets a close-date-only edit on a previously-PUBLISHED
        // election succeed; otherwise the delete-and-recreate would trip
        // the candidate_responses FK for any candidates with cast votes.
        if (existingTuples == incomingTuples) return
        if (existing.isNotEmpty() &&
            existing.any { candidateResponses.findByCandidateId(it.id).isNotEmpty() }
        ) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Candidates cannot be changed because votes have already been cast on this election. " +
                    "To adjust title or close date, use Super Admin → Manage All Polls."
            )
        }
        candidates.deleteAll(existing)
        if (inputs.isEmpty()) return
        candidates.saveAll(inputs.map { input ->
            val office = upsertOffice(input.officeName.trim(), input.officeDesc?.trim())
            Candidate(
                name = input.name.trim(),
                affiliation = input.affiliation.trim(),
                office = office,
                election = election
            )
        })
    }

    private fun upsertOffice(name: String, desc: String?): Office {
        offices.findByNameIgnoreCase(name)?.let { return it }
        return offices.save(Office(name = name, desc = desc ?: ""))
    }

    private fun validateClose(close: Instant?, confirmed: Boolean) {
        if (close == null) return
        if (close.isBefore(Instant.now())) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Close date must be in the future")
        }
        val daysOut = ChronoUnit.DAYS.between(Instant.now(), close)
        if (daysOut < 5 && !confirmed) {
            throw ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "close_date_short:$close"
            )
        }
    }
}
