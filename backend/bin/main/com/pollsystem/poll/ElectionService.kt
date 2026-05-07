package com.pollsystem.poll

import com.pollsystem.model.Candidate
import com.pollsystem.model.Election
import com.pollsystem.model.Office
import com.pollsystem.model.PollStatus
import com.pollsystem.model.User
import com.pollsystem.repository.CandidateRepository
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
    private val offices: OfficeRepository,
    private val pollTypes: PollTypeRepository
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
    fun toDto(e: Election): ElectionDto =
        ElectionDto.from(e, candidates.findByElectionId(e.id))

    private fun loadOwned(id: Long, creator: User): Election {
        val e = get(id)
        if (e.creator.id != creator.id) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }
        return e
    }

    private fun replaceCandidates(election: Election, inputs: List<CandidateInput>) {
        candidates.deleteAll(candidates.findByElectionId(election.id))
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
