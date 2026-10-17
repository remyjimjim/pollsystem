package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.AppFlag
import org.kodewerks.pollsystem.repository.AppFlagRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * The small-poll privacy threshold k: results hidden until k respondents,
 * public results in steps of k, groups under k withheld, the milestone email
 * at k. Normally app.results.k-anonymity-threshold (10; 3 in tests).
 *
 * Local development only (decided 2026-10-17): the Dev settings page can
 * override it, saved in app_flags so it survives rebuilds. The override is
 * read and settable only under the `local` profile; staging and prod always
 * use the configured value.
 */
@Component
class ResultsPrivacy(
    @Value("\${app.results.k-anonymity-threshold:10}") val configured: Int,
    private val flags: AppFlagRepository,
    env: Environment
) {
    private val local = env.acceptsProfiles(Profiles.of("local"))

    // Loaded on first use (not at startup, before the schema may be ready).
    @Volatile private var loaded = false
    @Volatile private var override: Int? = null

    fun k(): Int = currentOverride() ?: configured

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
