package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.AppFlag
import org.kodewerks.pollsystem.repository.AppFlagRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * The small-poll privacy threshold k: results hidden until k respondents,
 * public results in steps of k, the milestone email at k. Normally
 * app.results.k-anonymity-threshold (4; 3 in tests). Local dev may set 1 in
 * Dev settings, so totals show from the first response while testing.
 *
 * A narrowed group (an area filter, or respondents inside/outside the poll's
 * area) is withheld under [groupMinimum] (app.results.group-minimum, 4), so a
 * lone respondent in a zipcode can't be picked out even when k is 1.
 *
 * Getting started (decided 2026-11-03): until BallotBox has
 * app.results.launch-until-voters (100) different people who've answered
 * anything, k is app.results.launch-threshold (1), on staging and prod too.
 * The group minimum stays 4. When the 100th arrives, k becomes the configured
 * 4 and [LaunchPhaseWatcher] rebuilds every poll's snapshot, so polls under 4
 * respondents hide again. 0 turns the launch phase off (tests).
 *
 * Local development only (decided 2026-10-17): the Dev settings page can
 * override it, saved in app_flags so it survives rebuilds. The override is
 * read and settable only under the `local` profile; staging and prod always
 * use the configured value.
 */
@Component
class ResultsPrivacy(
    @Value("\${app.results.k-anonymity-threshold:4}") val configured: Int,
    @Value("\${app.results.group-minimum:4}") private val configuredGroupMinimum: Int,
    private val flags: AppFlagRepository,
    env: Environment,
    @Value("\${app.results.launch-threshold:1}") private val launchThreshold: Int = 1,
    @Value("\${app.results.launch-until-voters:100}") val launchUntilVoters: Int = 100,
    private val jdbc: JdbcTemplate? = null
) {
    private val local = env.acceptsProfiles(Profiles.of("local"))

    // Loaded on first use (not at startup, before the schema may be ready).
    @Volatile private var loaded = false
    @Volatile private var override: Int? = null

    fun k(): Int = currentOverride() ?: if (inLaunchPhase()) launchThreshold else configured

    // The launch phase: the voter count is read at most every 30 s, and once
    // it has reached the target it's over for good (in this process).
    @Volatile private var launchOver = false
    @Volatile private var sawLaunch = false
    @Volatile private var endedPending = false
    @Volatile private var countedAt = 0L
    /** How often the voter count is re-read (tests shorten it). */
    internal var recountEveryMs = 30_000L

    /** Different people who've answered anything: a ballot's candidates, a questionnaire, a measure. */
    fun totalVoters(): Int = jdbc?.queryForObject(
        """SELECT COUNT(*) FROM (SELECT user_id FROM candidate_responses UNION
           SELECT user_id FROM question_responses UNION SELECT user_id FROM ballot_responses) v""",
        Int::class.java
    ) ?: 0

    fun inLaunchPhase(): Boolean {
        if (launchUntilVoters <= 0 || launchOver) return false
        val now = System.currentTimeMillis()
        if (now - countedAt >= recountEveryMs) {
            countedAt = now
            if (totalVoters() >= launchUntilVoters) {
                launchOver = true
                if (sawLaunch) endedPending = true
            } else sawLaunch = true
        }
        return !launchOver
    }

    /** True once, when the launch phase has just ended in this process (time to rebuild snapshots). */
    @Synchronized
    fun takeLaunchEnded(): Boolean = endedPending.also { endedPending = false }

    /** The smallest narrowed group shown: never below k. */
    fun groupMinimum(): Int = maxOf(configuredGroupMinimum, k())

    /** The local override, or null (none set, or not local). */
    fun currentOverride(): Int? {
        if (!local) return null
        if (!loaded) {
            override = flags.findById(1).orElse(null)?.resultsThreshold
            loaded = true
        }
        return override
    }

    /** Set (1–100) or clear (null) the local override. Local profile only. */
    @Transactional
    fun setOverride(value: Int?) {
        check(local) { "The results threshold can only be changed in local development" }
        require(value == null || value in 1..100) { "The threshold must be between 1 and 100" }
        val current = flags.findById(1).orElse(AppFlag())
        flags.save(current.copy(resultsThreshold = value, updatedAt = Instant.now()))
        override = value
        loaded = true
    }
}

/**
 * When the launch phase ends (the 100th voter), rebuilds every poll's snapshot
 * for k = 4 so polls under 4 respondents hide again, the way changing the
 * local Dev setting does. Checks every minute; no emails.
 */
@Component
class LaunchPhaseWatcher(private val privacy: ResultsPrivacy, private val snapshots: ResultSnapshotBackfill) {
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    fun check() {
        privacy.inLaunchPhase()
        if (privacy.takeLaunchEnded()) snapshots.rebuildAll()
    }
}
