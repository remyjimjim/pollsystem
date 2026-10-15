package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.auth.MagicLinkProperties
import org.kodewerks.pollsystem.email.EmailService
import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.PollNotification
import org.kodewerks.pollsystem.model.PollNotificationKind
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.BallotMeasureRepository
import org.kodewerks.pollsystem.repository.ElectionRepository
import org.kodewerks.pollsystem.repository.PollNotificationRepository
import org.kodewerks.pollsystem.repository.QuestionnaireRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant

/**
 * Creator reports, phase 3: milestone emails to a poll's creator, each sent at
 * most once per poll (poll_notifications, V31).
 *
 * - RESULTS_VISIBLE when the poll's first results snapshot is taken (k
 *   respondents), i.e. the moment public results appear.
 * - CLOSED: a short final report once the close date has passed. A job checks
 *   every 15 minutes, for polls that closed in the last 7 days only (so a
 *   deploy doesn't email creators about long-closed polls).
 *
 * Emails never break what triggered them: a failure is logged, not thrown.
 */
@Service
class PollMilestoneService(
    private val questionnaires: QuestionnaireRepository,
    private val elections: ElectionRepository,
    private val ballotMeasures: BallotMeasureRepository,
    private val notifications: PollNotificationRepository,
    private val mutes: org.kodewerks.pollsystem.repository.PollEmailMuteRepository,
    private val participation: PollParticipationService,
    private val email: EmailService,
    private val links: MagicLinkProperties,
    @Value("\${app.results.k-anonymity-threshold:10}") private val k: Int,
    transactionManager: PlatformTransactionManager
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val tx = TransactionTemplate(transactionManager)

    private data class PollInfo(val kind: PollKind, val id: Long, val title: String, val creator: User, val status: PollStatus, val closeDate: Instant?)

    private fun info(kind: PollKind, id: Long): PollInfo? = when (kind) {
        PollKind.QUESTIONNAIRE -> questionnaires.findById(id).orElse(null)?.let { PollInfo(kind, it.id, it.title, it.creator, it.status, it.closeDate) }
        PollKind.ELECTION -> elections.findById(id).orElse(null)?.let { PollInfo(kind, it.id, it.title, it.creator, it.status, it.closeDate) }
        PollKind.BALLOT_MEASURE -> ballotMeasures.findById(id).orElse(null)?.let { PollInfo(kind, it.id, it.title, it.creator, it.status, it.closeDate) }
    }

    private fun slug(kind: PollKind) = when (kind) {
        PollKind.QUESTIONNAIRE -> "questionnaire"
        PollKind.ELECTION -> "election"
        PollKind.BALLOT_MEASURE -> "ballot-measure"
    }

    private fun site() = links.baseUrl.trimEnd('/')
    private fun reportUrl(p: PollInfo) = "${site()}/creator/polls/${slug(p.kind)}/${p.id}/report"
    private fun resultsUrl(p: PollInfo) = "${site()}/polls/${slug(p.kind)}/${p.id}/results"

    /**
     * Record the milestone (once) and email the creator. If the creator paused
     * this poll's emails, it's recorded but not sent, so un-pausing later
     * doesn't send it (missed emails are skipped). Returns false if already handled.
     */
    private fun once(p: PollInfo, kind: PollNotificationKind, subject: String, body: String): Boolean {
        if (notifications.existsByPollTypeAndPollIdAndKind(p.kind, p.id, kind)) return false
        notifications.save(PollNotification(pollType = p.kind, pollId = p.id, kind = kind))
        if (mutes.existsByPollTypeAndPollId(p.kind, p.id)) {
            log.info("Milestone email {} for {} {} skipped: paused by the creator", kind, p.kind, p.id)
            return true
        }
        try {
            email.send(p.creator.email, subject, body)
        } catch (e: Exception) {
            log.warn("Milestone email {} for {} {} failed: {}", kind, p.kind, p.id, e.message)
        }
        return true
    }

    /** The poll's first results snapshot was just taken: its public results are now visible. */
    fun resultsVisible(kind: PollKind, pollId: Long) {
        val p = info(kind, pollId) ?: return
        once(
            p, PollNotificationKind.RESULTS_VISIBLE,
            "Your poll \"${p.title}\" has $k responses: results are now visible",
            """
            |Your poll "${p.title}" has reached $k responses, so its results are now visible to everyone.
            |
            |They show as percentages and update every $k responses, to protect respondents' privacy.
            |
            |Results: ${resultsUrl(p)}
            |Your report: ${reportUrl(p)}
            """.trimMargin()
        )
    }

    /** Polls that closed in the last 7 days and haven't had their final report yet. */
    @Scheduled(fixedDelayString = "\${app.creator-reports.close-check-interval-ms:900000}", initialDelay = 60_000)
    fun sendClosingReports() {
        val now = Instant.now()
        val since = now.minus(Duration.ofDays(7))
        val closed = (questionnaires.findAll().map { PollKind.QUESTIONNAIRE to it.id } +
            elections.findAll().map { PollKind.ELECTION to it.id } +
            ballotMeasures.findAll().map { PollKind.BALLOT_MEASURE to it.id })
        var sent = 0
        for ((kind, id) in closed) {
            tx.executeWithoutResult {
                val p = info(kind, id) ?: return@executeWithoutResult
                val close = p.closeDate ?: return@executeWithoutResult
                if (p.status != PollStatus.PUBLISHED && p.status != PollStatus.CLOSED) return@executeWithoutResult
                if (close.isAfter(now) || close.isBefore(since)) return@executeWithoutResult
                if (closingReport(p)) sent++
            }
        }
        if (sent > 0) log.info("Sent {} closing reports", sent)
    }

    private fun closingReport(p: PollInfo): Boolean {
        val part = participation.of(p.kind, p.id)
        val inArea = when {
            part.respondents == 0 -> ""
            part.inArea == null -> "\nInside the poll's area: withheld (a group is under $k people)."
            else -> "\nInside the poll's area: ${pct(part.inArea, part.respondents)}%."
        }
        val results = if (part.respondents >= k) "Results: ${resultsUrl(p)}"
                      else "Its results stay hidden: it had fewer than $k respondents."
        return once(
            p, PollNotificationKind.CLOSED,
            "Your poll \"${p.title}\" has closed",
            """
            |Your poll "${p.title}" has closed.
            |
            |Respondents: ${part.respondents}$inArea
            |
            |$results
            |Your full report: ${reportUrl(p)}
            """.trimMargin()
        )
    }
}
