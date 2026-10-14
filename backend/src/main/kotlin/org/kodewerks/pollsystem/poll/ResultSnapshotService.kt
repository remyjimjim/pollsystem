package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.ResultSnapshot
import org.kodewerks.pollsystem.model.ResultSnapshotAnswer
import org.kodewerks.pollsystem.repository.BallotMeasureRepository
import org.kodewerks.pollsystem.repository.BallotResponseRepository
import org.kodewerks.pollsystem.repository.CandidateResponseRepository
import org.kodewerks.pollsystem.repository.CountyRepository
import org.kodewerks.pollsystem.repository.CountyZipsRepository
import org.kodewerks.pollsystem.repository.ElectionRepository
import org.kodewerks.pollsystem.repository.QuestionResponseRepository
import org.kodewerks.pollsystem.repository.QuestionnaireRepository
import org.kodewerks.pollsystem.repository.ResultSnapshotAnswerRepository
import org.kodewerks.pollsystem.repository.ResultSnapshotRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import kotlin.math.roundToInt

/** One answer, whichever the poll type: a question's text, or "true"/"false" for a candidate or measure. */
data class Answer(val userId: Long, val zipcode: String?, val itemId: Long, val response: String)

/**
 * How results are shown (small-poll privacy, decided 2026-10-14):
 * - HIDDEN: fewer than [minimum] people have responded; no results.
 * - STEPPED: the public view, frozen at the last step of [step] respondents,
 *   as rounded percentages only.
 * - LIVE: a super's view, live and with raw counts.
 */
data class ResultsVisibility(
    val mode: String,
    /** Everyone who has responded so far (participation, not how they answered). */
    val respondentsSoFar: Int,
    val minimum: Int,
    val step: Int
)

internal fun pct(n: Int, of: Int): Int = if (of <= 0) 0 else (n * 100.0 / of).roundToInt()

/**
 * Freezes a poll's answers each time its respondent count reaches the next
 * multiple of k (app.results.k-anonymity-threshold). Public results are
 * computed from that copy (V30), so a single vote or a single changed answer
 * is never visible on its own. Called after every submission; edits don't
 * change the count, so they appear only with the next step.
 */
@Service
class ResultSnapshotService(
    private val questionResponses: QuestionResponseRepository,
    private val candidateResponses: CandidateResponseRepository,
    private val ballotResponses: BallotResponseRepository,
    private val snapshots: ResultSnapshotRepository,
    private val snapshotAnswers: ResultSnapshotAnswerRepository,
    @Value("\${app.results.k-anonymity-threshold:10}") private val k: Int
) {
    fun live(kind: PollKind, pollId: Long): List<Answer> = when (kind) {
        PollKind.QUESTIONNAIRE -> questionResponses.findByQuestionnaireId(pollId)
            .map { Answer(it.user.id, it.user.zipcode, it.question.id, it.response) }
        PollKind.ELECTION -> candidateResponses.findByElectionId(pollId)
            .map { Answer(it.user.id, it.user.zipcode, it.candidate.id, it.response.toString()) }
        PollKind.BALLOT_MEASURE -> ballotResponses.findByMeasureId(pollId)
            .map { Answer(it.user.id, it.user.zipcode, pollId, it.response.toString()) }
    }

    /** Take a new snapshot if the respondent count has reached a step beyond the current one. */
    @Transactional
    fun refresh(kind: PollKind, pollId: Long) {
        val answers = live(kind, pollId)
        val n = answers.map { it.userId }.distinct().size
        val step = n / k * k
        if (step < k) return
        val current = snapshots.findByPollTypeAndPollId(kind, pollId)
        if (current != null && current.respondents >= step) return
        val snap = snapshots.save(
            current?.copy(respondents = n, takenAt = Instant.now())
                ?: ResultSnapshot(pollType = kind, pollId = pollId, respondents = n)
        )
        snapshotAnswers.deleteBySnapshotId(snap.id)
        snapshotAnswers.saveAll(answers.map {
            ResultSnapshotAnswer(snapshotId = snap.id, userId = it.userId, zipcode = it.zipcode, itemId = it.itemId, response = it.response)
        })
    }

    fun snapshot(kind: PollKind, pollId: Long): List<Answer>? =
        snapshots.findByPollTypeAndPollId(kind, pollId)?.let { s ->
            snapshotAnswers.findBySnapshotId(s.id).map { Answer(it.userId, it.zipcode, it.itemId, it.response) }
        }
}

/** The answers a results request may see, already filtered, plus how to show them. */
data class PreparedResults(
    val visibility: ResultsVisibility,
    /** The filtered answers (empty when hidden or suppressed). */
    val answers: List<Answer>,
    /** Distinct respondents in the filtered group. */
    val respondents: Int,
    val suppressed: Boolean,
    val filterApplied: Map<String, String>?
) {
    val live get() = visibility.mode == "LIVE"
}

/** Shared by the three results endpoints: who sees what (live or stepped), the geo / purview filters, and k. */
@Component
class ResultsAccess(
    private val snapshots: ResultSnapshotService,
    private val purviews: PollPurviewService,
    private val counties: CountyRepository,
    private val countyZips: CountyZipsRepository,
    @Value("\${app.results.k-anonymity-threshold:10}") private val k: Int
) {
    fun prepare(
        principal: AppUserDetails?,
        kind: PollKind,
        pollId: Long,
        zipcodes: List<String>?,
        stateIds: List<Long>?,
        countyIds: List<Long>?,
        withinPurview: Boolean,
        outsidePurview: Boolean
    ): PreparedResults {
        val liveAnswers = snapshots.live(kind, pollId)
        val soFar = liveAnswers.map { it.userId }.distinct().size
        val isSuper = principal?.user?.access == AccessLevel.SUPER
        val filterMap = describeFilter(zipcodes, stateIds, countyIds, withinPurview, outsidePurview)
        val base: List<Answer> = when {
            isSuper -> liveAnswers
            else -> snapshots.snapshot(kind, pollId)
                ?: return PreparedResults(ResultsVisibility("HIDDEN", soFar, k, k), emptyList(), 0, false, filterMap)
        }
        val visibility = ResultsVisibility(if (isSuper) "LIVE" else "STEPPED", soFar, k, k)

        // A responder with no zipcode (profile not yet completed) can't fall in
        // any geographic group, so they're excluded from a filtered/purview view.
        val geoZips = resolveGeoFilter(zipcodes, stateIds, countyIds, counties, countyZips)
        var filtered = base
        if (geoZips != null) filtered = filtered.filter { it.zipcode?.let { z -> z in geoZips } == true }
        filtered = filterByPurview(filtered, { it.zipcode }, purviews.purviewOf(kind, pollId), withinPurview, outsidePurview, purviews)

        val respondents = filtered.map { it.userId }.distinct().size
        val narrowed = geoZips != null || !(withinPurview && outsidePurview)
        // Supers see raw numbers at all times; everyone else, no group under k.
        if (!isSuper && narrowed && respondents < k) {
            return PreparedResults(visibility, emptyList(), 0, true, filterMap)
        }
        return PreparedResults(visibility, filtered, respondents, false, filterMap)
    }
}

/** Existing polls with k+ respondents get a snapshot at startup, so their public results don't vanish. */
@Component
class ResultSnapshotBackfill(
    private val snapshots: ResultSnapshotService,
    private val questionnaires: QuestionnaireRepository,
    private val elections: ElectionRepository,
    private val ballotMeasures: BallotMeasureRepository,
    transactionManager: PlatformTransactionManager
) : ApplicationRunner {
    private val tx = TransactionTemplate(transactionManager)
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val polls = questionnaires.findAll().map { PollKind.QUESTIONNAIRE to it.id } +
            elections.findAll().map { PollKind.ELECTION to it.id } +
            ballotMeasures.findAll().map { PollKind.BALLOT_MEASURE to it.id }
        polls.forEach { (kind, id) -> tx.executeWithoutResult { snapshots.refresh(kind, id) } }
        log.info("Result snapshots checked for {} polls", polls.size)
    }
}
