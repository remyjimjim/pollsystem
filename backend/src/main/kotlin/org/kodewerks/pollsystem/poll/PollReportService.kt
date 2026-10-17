package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.repository.BallotResponseRepository
import org.kodewerks.pollsystem.repository.CandidateResponseRepository
import org.kodewerks.pollsystem.repository.CountyRepository
import org.kodewerks.pollsystem.repository.CountyZipsRepository
import org.kodewerks.pollsystem.repository.QuestionResponseRepository
import org.kodewerks.pollsystem.repository.StateRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** A period of new respondents: [from]..[to] (UTC days), [count] new, [runningTotal] so far. */
data class TimelineBucket(val from: LocalDate, val to: LocalDate, val count: Int, val runningTotal: Int)

/** Respondents in one sub-area of the poll's area; [count] null = fewer than the privacy threshold. */
data class ReachRow(val label: String, val count: Int?)

data class Reach(
    /** The level the area is broken down by: STATE, COUNTY or ZIP. */
    val level: ScopeLevel,
    val rows: List<ReachRow>,
    /** Respondents living outside the poll's area; null = withheld. */
    val outside: Int?,
    /** Sub-areas with at least one respondent, of [total] in the poll's area. */
    val covered: Int,
    val total: Int
)

data class Engagement(val changedAnswers: Int, val commented: Int)

data class PollReport(
    val respondents: Int,
    /** Respondents living inside the poll's area; null = withheld (see PollParticipationService). */
    val inArea: Int?,
    /** The privacy threshold the report applies. */
    val threshold: Int,
    /** Null until the poll has at least [threshold] respondents. */
    val timeline: List<TimelineBucket>?,
    val reach: Reach?,
    val engagement: Engagement?
)

/**
 * Creator reports, phase 2: one poll's report for its creator. Privacy
 * guardrails (todo.md): totals only; any group under the k-anonymity
 * threshold is withheld, plus one more when a single withheld cell could be
 * worked out by subtraction; the timeline is merged into periods of at least
 * k new respondents; comments are counted, never shown. Sections that need
 * more respondents than the poll has are left out (null).
 */
private const val OUTSIDE = "\u0000outside"

@Service
class PollReportService(
    private val questionResponses: QuestionResponseRepository,
    private val candidateResponses: CandidateResponseRepository,
    private val ballotResponses: BallotResponseRepository,
    private val purviews: PollPurviewService,
    private val participation: PollParticipationService,
    private val countyZips: CountyZipsRepository,
    private val counties: CountyRepository,
    private val states: StateRepository,
    private val privacy: ResultsPrivacy
) {
    private val k: Int get() = privacy.k()
    private data class Respondent(val zipcode: String?, val first: Instant, val changed: Boolean, val commented: Boolean)

    @Transactional(readOnly = true)
    fun report(kind: PollKind, pollId: Long): PollReport {
        val people = respondents(kind, pollId)
        val enough = people.size >= k
        return PollReport(
            respondents = people.size,
            inArea = participation.of(kind, pollId).inArea,
            threshold = k,
            timeline = if (enough) timeline(people.map { it.first }) else null,
            reach = if (enough) reach(kind, pollId, people) else null,
            engagement = if (enough) Engagement(people.count { it.changed }, people.count { it.commented }) else null
        )
    }

    /** One entry per person: their zipcode, first submission, and whether they ever changed or commented. */
    private fun respondents(kind: PollKind, pollId: Long): List<Respondent> {
        data class Row(val userId: Long, val zip: String?, val submitted: Instant, val modified: Instant?, val comment: String?)
        val rows: List<Row> = when (kind) {
            PollKind.QUESTIONNAIRE -> questionResponses.findByQuestionnaireId(pollId)
                .map { Row(it.user.id, it.user.zipcode, it.dateSubmitted, it.lastModified, it.comment) }
            PollKind.ELECTION -> candidateResponses.findByElectionId(pollId)
                .map { Row(it.user.id, it.user.zipcode, it.dateSubmitted, it.lastModified, it.comment) }
            PollKind.BALLOT_MEASURE -> ballotResponses.findByMeasureId(pollId)
                .map { Row(it.user.id, it.user.zipcode, it.dateSubmitted, it.lastModified, it.comment) }
        }
        return rows.groupBy { it.userId }.values.map { rs ->
            Respondent(
                zipcode = rs.first().zip,
                first = rs.minOf { it.submitted },
                changed = rs.any { it.modified != null },
                commented = rs.any { !it.comment.isNullOrBlank() }
            )
        }
    }

    /** New respondents per UTC day, merged forward so every period has at least k (the last joins the one before). */
    internal fun timeline(firsts: List<Instant>): List<TimelineBucket> {
        val perDay = firsts.groupingBy { it.atZone(ZoneOffset.UTC).toLocalDate() }.eachCount().toSortedMap()
        val periods = mutableListOf<Triple<LocalDate, LocalDate, Int>>()
        var start: LocalDate? = null
        var count = 0
        for ((day, n) in perDay) {
            val from = start ?: day
            start = from
            count += n
            if (count >= k) { periods.add(Triple(from, day, count)); start = null; count = 0 }
        }
        val leftover = start
        if (leftover != null) {
            val last = periods.removeLastOrNull()
            periods.add(
                if (last == null) Triple(leftover, perDay.lastKey(), count)
                else Triple(last.first, perDay.lastKey(), last.third + count)
            )
        }
        var running = 0
        return periods.map { (from, to, n) -> running += n; TimelineBucket(from, to, n, running) }
    }

    private fun reach(kind: PollKind, pollId: Long, people: List<Respondent>): Reach {
        val rows = purviews.purviewOf(kind, pollId)
        val nationwide = rows.isEmpty() || rows.any { it.scopeLevel == ScopeLevel.NATIONAL }
        val within = purviews.classifyZips(rows, people.map { it.zipcode })
        val inside = people.filter { within[it.zipcode] == true }
        val meta = countyZips.findByZipcodeIn(inside.mapNotNull { it.zipcode }.distinct()).groupBy { it.zipcode }

        // Break the area down one level: nation -> states, states -> counties, counties/zips -> zips.
        val level: ScopeLevel
        val labelOf: (String) -> String?        // zipcode -> sub-area label
        val total: Int
        when {
            nationwide -> {
                level = ScopeLevel.STATE
                labelOf = { z -> meta[z]?.firstOrNull()?.county?.state?.name }
                total = states.count().toInt()
            }
            rows.all { it.scopeLevel == ScopeLevel.STATE } -> {
                level = ScopeLevel.COUNTY
                labelOf = { z -> meta[z]?.firstOrNull()?.county?.let { "${it.name} (${it.state.initial})" } }
                total = counties.findByStateIdIn(rows.mapNotNull { it.stateId }).size
            }
            else -> {
                level = ScopeLevel.ZIP
                labelOf = { z -> z }
                val countyIds = rows.mapNotNull { if (it.scopeLevel == ScopeLevel.COUNTY) it.countyId else null }
                val zips = rows.mapNotNull { if (it.scopeLevel == ScopeLevel.ZIP) it.zipcode else null }.toSet() +
                    (if (countyIds.isEmpty()) emptySet() else countyZips.findByCountyIdIn(countyIds).map { it.zipcode }.toSet())
                total = zips.size
            }
        }
        val counts = inside.mapNotNull { p -> p.zipcode?.let(labelOf) }.groupingBy { it }.eachCount()
        // "Outside the area" is a cell too: with the sub-areas it adds up to the
        // respondent total shown above, so it's suppressed together with them.
        val cells = suppress(counts + (OUTSIDE to (people.size - inside.size)))
        return Reach(
            level = level,
            rows = cells.filter { it.label != OUTSIDE }.sortedWith(compareBy({ -(it.count ?: 0) }, { it.label })),
            outside = cells.single { it.label == OUTSIDE }.count,
            covered = counts.size,
            total = total
        )
    }

    /**
     * The cells partition a total that's shown (every respondent is in exactly
     * one), so: withhold every count from 1 to k-1 (zero reveals no one), and if
     * that leaves exactly one withheld cell, it could be worked out from the
     * total by subtraction, so withhold the smallest shown non-zero cell too.
     */
    internal fun suppress(counts: Map<String, Int>): List<ReachRow> {
        val hidden = counts.filterValues { it in 1 until k }.keys.toMutableSet()
        if (hidden.size == 1) {
            counts.filter { (key, n) -> key !in hidden && n > 0 }.minByOrNull { it.value }?.let { hidden += it.key }
        }
        return counts.map { (label, n) -> ReachRow(label, if (label in hidden) null else n) }
    }
}
