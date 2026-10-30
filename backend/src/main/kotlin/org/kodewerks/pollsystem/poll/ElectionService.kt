package org.kodewerks.pollsystem.poll

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.Candidate
import org.kodewerks.pollsystem.model.Election
import org.kodewerks.pollsystem.model.Office
import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.CandidateRepository
import org.kodewerks.pollsystem.repository.CandidateResponseRepository
import org.kodewerks.pollsystem.repository.ElectionRepository
import org.kodewerks.pollsystem.repository.OfficeRepository
import org.kodewerks.pollsystem.repository.PollTypeRepository
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
    private val purviews: PollPurviewService,
    private val grants: CreatorGrantGuard,
    private val objectMapper: ObjectMapper,
    private val ballotMeasures: BallotMeasureService,
    private val includes: ElectionIncludes,
    private val history: PollHistoryService,
    private val rankedRaces: RankedRaces
) {

    @Transactional
    fun saveDraft(creator: User, dto: ElectionDraftRequest): Election {
        requireCreator(creator)
        val pt = pollTypes.findById(dto.pollTypeId).orElseThrow {
            ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown poll type")
        }
        val saved = elections.save(
            Election(
                creator = creator,
                pollType = pt,
                title = dto.title.trim(),
                date = dto.date,
                zipcode = null,
                status = PollStatus.DRAFT,
                closeDate = dto.closeDate,
                residentsOnly = dto.residentsOnly ?: false
            )
        )
        replaceCandidates(saved, dto.candidates)
        applyPurview(saved.id, dto)
        applyIncludes(saved.id, creator, dto.includedElectionIds)
        grants.requireCovers(creator, pt, purviews.purviewOf(PollKind.ELECTION, saved.id))
        return saved
    }

    @Transactional
    fun update(id: Long, creator: User, dto: ElectionDraftRequest): Election {
        requireCreator(creator)
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
                zipcode = null,
                closeDate = dto.closeDate,
                residentsOnly = dto.residentsOnly ?: existing.residentsOnly,
                creatorEditedAt = Instant.now()
            )
        )
        replaceCandidates(updated, dto.candidates)
        applyPurview(updated.id, dto)
        applyIncludes(updated.id, creator, dto.includedElectionIds)
        grants.requireCovers(creator, pt, purviews.purviewOf(PollKind.ELECTION, updated.id))
        return updated
    }

    /**
     * The elections shown on this one's ballot (V37); null leaves them as they
     * are. One level: an included election can't include others, and a ballot
     * that includes others can't itself be included. Only your own elections,
     * unless you're a super.
     */
    private fun applyIncludes(electionId: Long, creator: User, ids: List<Long>?) {
        if (ids == null) return
        val wanted = ids.distinct()
        fun bad(msg: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, msg)
        if (electionId in wanted) bad("An election can't include itself")
        if (wanted.isNotEmpty() && includes.includedBy(electionId).isNotEmpty()) {
            bad("This election is included on another ballot, so it can't include others")
        }
        for (id in wanted) {
            val other = elections.findById(id).orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown election $id") }
            if (other.creator.id != creator.id && creator.access != AccessLevel.SUPER) {
                throw ResponseStatusException(HttpStatus.FORBIDDEN, "You can only include your own elections")
            }
            if (includes.of(id).isNotEmpty()) bad("\"${other.title}\" includes other elections, so it can't be included")
        }
        includes.replace(electionId, wanted)
    }

    /** Elections carry a County/State/Nationwide purview (poll_purviews); ZIP is rejected. */
    private fun applyPurview(electionId: Long, dto: ElectionDraftRequest) {
        if (dto.scopeLevel == ScopeLevel.ZIP) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Elections use a County, State, or Nationwide purview"
            )
        }
        purviews.replacePurview(PollKind.ELECTION, electionId, dto.scopeLevel, dto.regionIds)
    }

    @Transactional
    fun publish(id: Long, creator: User, confirmed: Boolean): Election {
        requireCreator(creator)
        val existing = loadOwned(id, creator)
        if (existing.status != PollStatus.DRAFT) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT can be published")
        }
        val electionCandidates = candidates.findByElectionId(id)
        // An election needs something to vote on: candidates, or ballot measures
        // (statewide measures with no statewide races), or included elections (a
        // district whose House race is unopposed and off the ballot still gets
        // the statewide races).
        if (electionCandidates.isEmpty() && ballotMeasures.draftsOf(id).isEmpty() && includes.of(id).isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one candidate, ballot measure or included election required")
        }
        // A re-publish is identified by the election having cast votes
        // already. Election entities don't carry a publish-time tracker
        // (date_submitted is set at row creation), so this is the
        // closest proxy: any vote means the poll has been live before.
        val hasBeenLive = electionCandidates.any {
            candidateResponses.findByCandidateId(it.id).isNotEmpty()
        }
        validateClose(existing.closeDate, confirmed, allowPast = hasBeenLive)
        // Re-checked at publish: grants may have been disabled since the draft.
        grants.requireCovers(creator, existing.pollType, purviews.purviewOf(PollKind.ELECTION, id))
        // Its draft ballot measures go live with it. Every one is checked like
        // a measure's own publish BEFORE anything is saved, so one that can't
        // go live stops the whole publish cleanly.
        val drafts = ballotMeasures.draftsOf(id)
        for (m in drafts) {
            try {
                ballotMeasures.checkPublishable(m, creator, confirmed)
            } catch (e: ResponseStatusException) {
                val reason = e.reason ?: ""
                // Keep the short-close code as-is so the page can ask to confirm.
                if (reason.startsWith("close_date_short:")) throw e
                throw ResponseStatusException(e.statusCode, "Ballot measure \"${m.title}\": $reason")
            }
        }
        val published = elections.save(existing.copy(status = PollStatus.PUBLISHED, creatorEditedAt = Instant.now()))
        history.published(PollKind.ELECTION, id, creator)
        drafts.forEach { ballotMeasures.publishChecked(it.copy(election = published), creator, confirmed) }
        return published
    }

    @Transactional(readOnly = true)
    fun get(id: Long): Election = elections.findById(id).orElseThrow {
        ResponseStatusException(HttpStatus.NOT_FOUND, "Election not found")
    }

    @Transactional(readOnly = true)
    fun toDto(e: Election): ElectionDto {
        val (widget, groupBy) = readCandidatesHints(e.pollType.templateJson)
        val purviewRows = purviews.purviewOf(PollKind.ELECTION, e.id)
        return ElectionDto.from(
            e,
            candidates.findByElectionId(e.id),
            scopeLevel = purviews.scopeLevelOf(purviewRows),
            regionIds = purviews.regionIdsOf(purviewRows),
            regionStateIds = purviews.regionStateIdsOf(purviewRows),
            regionLabel = purviews.regionLabel(purviewRows),
            candidatesWidget = widget,
            candidatesGroupBy = groupBy,
            includedElectionIds = includes.of(e.id),
            includedByElectionIds = includes.includedBy(e.id),
            rankedOfficeIds = rankedRaces.of(e.id).sorted()
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
