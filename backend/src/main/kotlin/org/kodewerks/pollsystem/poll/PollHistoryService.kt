package org.kodewerks.pollsystem.poll

import com.fasterxml.jackson.databind.ObjectMapper
import org.kodewerks.pollsystem.model.PollEvent
import org.kodewerks.pollsystem.model.PollEventType
import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.BallotMeasureRepository
import org.kodewerks.pollsystem.repository.CandidateRepository
import org.kodewerks.pollsystem.repository.ElectionRepository
import org.kodewerks.pollsystem.repository.PollEventRepository
import org.kodewerks.pollsystem.repository.QuestionRepository
import org.kodewerks.pollsystem.repository.QuestionnaireRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A poll's history (V34, decided 2026-10-17): published, reopened, closed,
 * archived, restored, switched off/on by an admin, edited by an admin or super.
 * Each publish keeps a snapshot of what voters see (title, summary, close date,
 * questions or candidates, area) and records what changed since the previous
 * publish, e.g. "Close date: 2026-11-03 18:00 UTC → 2026-11-10 18:00 UTC".
 */
@Service
class PollHistoryService(
    private val events: PollEventRepository,
    private val questionnaires: QuestionnaireRepository,
    private val questions: QuestionRepository,
    private val elections: ElectionRepository,
    private val candidates: CandidateRepository,
    private val ballotMeasures: BallotMeasureRepository,
    private val purviews: PollPurviewService,
    private val objectMapper: ObjectMapper
) {
    @Transactional
    fun record(kind: PollKind, pollId: Long, type: PollEventType, actor: User?, details: String? = null,
               at: Instant = Instant.now(), snapshot: String? = null): PollEvent =
        events.save(PollEvent(pollType = kind, pollId = pollId, event = type, actor = actor, at = at,
            details = details?.takeIf { it.isNotBlank() },
            // An edit keeps the poll as it is now, so the History page can show its Diff.
            snapshot = snapshot ?: if (type == PollEventType.EDITED) snapshotOf(kind, pollId)?.let(objectMapper::writeValueAsString) else null))

    /** Before and after for one entry: its snapshot and the previous one (null: nothing to compare). */
    fun diff(event: PollEvent): Pair<Snapshot, Snapshot>? {
        val now = event.snapshot ?: return null
        val before = events.findByPollTypeAndPollIdOrderByIdDesc(event.pollType, event.pollId)
            .firstOrNull { it.id < event.id && it.snapshot != null }?.snapshot ?: return null
        return objectMapper.readValue(before, Snapshot::class.java) to objectMapper.readValue(now, Snapshot::class.java)
    }

    /** Published (again): snapshot what voters see, and say what changed since the last publish. */
    @Transactional
    fun published(kind: PollKind, pollId: Long, actor: User?) {
        val now = snapshotOf(kind, pollId) ?: return
        val previous = events.findByPollTypeAndPollIdOrderByIdDesc(kind, pollId)
            .firstOrNull { it.event == PollEventType.PUBLISHED && it.snapshot != null }
            ?.snapshot?.let { objectMapper.readValue(it, Snapshot::class.java) }
        record(kind, pollId, PollEventType.PUBLISHED, actor,
            details = previous?.let { changes(it, now).joinToString("\n") },
            snapshot = objectMapper.writeValueAsString(now))
    }

    /**
     * Record that the poll closed (its close date passed), once per time it was
     * live: only if there's no CLOSED entry since its latest publish.
     */
    @Transactional
    fun closedIfNew(kind: PollKind, pollId: Long, closeDate: Instant) {
        val history = events.findByPollTypeAndPollIdOrderByIdDesc(kind, pollId)
        val sincePublish = history.takeWhile {
            it.event != PollEventType.PUBLISHED && it.event != PollEventType.PUBLISHED_BEFORE_HISTORY
        }
        if (sincePublish.any { it.event == PollEventType.CLOSED }) return
        val publishedAt = history.firstOrNull {
            it.event == PollEventType.PUBLISHED || it.event == PollEventType.PUBLISHED_BEFORE_HISTORY
        }?.at
        // A poll re-published with a close date already past closes as it goes live.
        val at = if (publishedAt != null && closeDate.isBefore(publishedAt)) publishedAt else closeDate
        record(kind, pollId, PollEventType.CLOSED, actor = null, at = at)
    }

    // ---------- snapshots ----------

    data class Snapshot(
        val title: String = "",
        val summary: String? = null,
        val closeDate: Instant? = null,
        val items: List<String> = emptyList(),
        val area: String = ""
    )

    private fun snapshotOf(kind: PollKind, pollId: Long): Snapshot? {
        val area = purviews.regionLabel(purviews.purviewOf(kind, pollId))
        return when (kind) {
            PollKind.QUESTIONNAIRE -> questionnaires.findById(pollId).orElse(null)?.let { q ->
                Snapshot(q.title, q.summary, q.closeDate, questions.findByQuestionnaireId(pollId).map { it.question }, area)
            }
            PollKind.ELECTION -> elections.findById(pollId).orElse(null)?.let { e ->
                Snapshot(e.title, null, e.closeDate,
                    candidates.findByElectionId(pollId).map { "${it.name} (${it.affiliation}), ${it.office.name}" }, area)
            }
            PollKind.BALLOT_MEASURE -> ballotMeasures.findById(pollId).orElse(null)?.let { m ->
                Snapshot(m.title, m.summary, m.closeDate, emptyList(), area)
            }
        }
    }

    private fun changes(before: Snapshot, after: Snapshot): List<String> = buildList {
        if (before.title != after.title) add("Title: \"${before.title}\" → \"${after.title}\"")
        if (before.summary != after.summary) add("Summary changed")
        if (before.closeDate != after.closeDate) add("Close date: ${fmt(before.closeDate)} → ${fmt(after.closeDate)}")
        if (before.items != after.items) add("Questions or candidates changed")
        if (before.area != after.area) add("Area: ${before.area} → ${after.area}")
    }

    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC)
    private fun fmt(i: Instant?) = i?.let { stamp.format(it) } ?: "none"
}
