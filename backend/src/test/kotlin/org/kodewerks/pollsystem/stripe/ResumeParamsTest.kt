package org.kodewerks.pollsystem.stripe

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** "Keep my membership" sends Stripe only one of cancel_at_period_end / cancel_at (2026-10-25: both together was refused). */
class ResumeParamsTest {

    @Test
    fun `set to end at period end, it clears only cancel_at_period_end`() {
        val p = resumeParams(cancelAtPeriodEnd = true, cancelAt = 1_800_000_000L)!!.toMap()
        assertThat(p).containsEntry("cancel_at_period_end", false).doesNotContainKey("cancel_at")
    }

    @Test
    fun `set to end on a date (Stripe's portal), it clears only cancel_at`() {
        val p = resumeParams(cancelAtPeriodEnd = false, cancelAt = 1_800_000_000L)!!.toMap()
        assertThat(p).containsKey("cancel_at").doesNotContainKey("cancel_at_period_end")
    }

    @Test
    fun `not set to end, there's nothing to send`() {
        assertThat(resumeParams(cancelAtPeriodEnd = false, cancelAt = null)).isNull()
    }
}
