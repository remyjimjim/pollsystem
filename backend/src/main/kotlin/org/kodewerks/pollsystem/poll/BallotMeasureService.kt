package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.BallotMeasure
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.BallotMeasureRepository
import org.kodewerks.pollsystem.repository.BallotResponseRepository
import org.kodewerks.pollsystem.repository.ElectionRepository
import org.kodewerks.pollsystem.repository.PollTypeRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class BallotMeasureService(
    private val measures: BallotMeasureRepository,
    private val ballotResponses: BallotResponseRepository,
    private val elections: ElectionRepository,
    private val pollTypes: PollTypeRepository
) {

    @Transactional
    fun saveDraft(creator: User, dto: BallotMeasureDraftRequest): BallotMeasure {
        val pt = pollTypes.findById(dto.pollTypeId).orElseThrow {
            ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown poll type")
        }
        val election = elections.findById(dto.electionId).orElseThrow {
            ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown election")
        }
        if (election.creator.id != creator.id) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You can only attach ballot measures to elections you created"
            )
        }
        return measures.save(
            BallotMeasure(
                creator = creator,
                pollType = pt,
                title = dto.title.trim(),
                summary = dto.summary.trim(),
                election = election,
                effectiveDate = dto.effectiveDate,
                status = PollStatus.DRAFT,
                closeDate = dto.closeDate
            )
        )
    }

    @Transactional
    fun update(id: Long, creator: User, dto: BallotMeasureDraftRequest): BallotMeasure {
        val existing = loadOwned(id, creator)
        if (existing.status != PollStatus.DRAFT) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT can be edited")
        }
        val pt = pollTypes.findById(dto.pollTypeId).orElseThrow {
            ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown poll type")
        }
        val election = elections.findById(dto.electionId).orElseThrow {
            ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown election")
        }
        if (election.creator.id != creator.id) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "You can only attach ballot measures to elections you created"
            )
        }
        return measures.save(
            existing.copy(
                pollType = pt,
                election = election,
                title = dto.title.trim(),
                summary = dto.summary.trim(),
                effectiveDate = dto.effectiveDate,
                closeDate = dto.closeDate,
                lastUpdated = Instant.now()
            )
        )
    }

    @Transactional
    fun publish(id: Long, creator: User, confirmed: Boolean): BallotMeasure {
        val existing = loadOwned(id, creator)
        if (existing.status != PollStatus.DRAFT) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT can be published")
        }
        // A re-publish (archive + restore + re-edit) on a ballot measure
        // that already has cast votes is allowed to take a past close
        // date; date_submitted is set at draft creation so isn't useful
        // as the "has been live" signal, but the response count is.
        val hasBeenLive = ballotResponses.findByMeasureId(id).isNotEmpty()
        validateClose(existing.closeDate, confirmed, allowPast = hasBeenLive)
        return measures.save(
            existing.copy(status = PollStatus.PUBLISHED, lastUpdated = Instant.now())
        )
    }

    @Transactional(readOnly = true)
    fun get(id: Long): BallotMeasure = measures.findById(id).orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND, "Ballot measure not found")
    }

    private fun loadOwned(id: Long, creator: User): BallotMeasure {
        val bm = get(id)
        if (bm.creator.id != creator.id) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN)
        }
        return bm
    }

    private fun validateClose(close: Instant?, confirmed: Boolean, allowPast: Boolean = false) {
        if (close == null) return
        if (!allowPast && close.isBefore(Instant.now())) {
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
