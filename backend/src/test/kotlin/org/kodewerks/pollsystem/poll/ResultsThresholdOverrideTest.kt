package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.model.AppFlag
import org.kodewerks.pollsystem.repository.AppFlagRepository
import org.springframework.beans.factory.annotation.Autowired

/** Outside the local profile (as on staging and prod) the threshold can't be overridden. */
class ResultsThresholdOverrideTest : AbstractIntegrationTest() {

    @Autowired private lateinit var privacy: ResultsPrivacy
    @Autowired private lateinit var flags: AppFlagRepository

    @Test
    fun `a saved override is ignored and can't be set`() {
        flags.save(flags.findById(1).orElse(AppFlag()).copy(resultsThreshold = 1))
        assertThat(privacy.k()).isEqualTo(privacy.configured)
        assertThat(privacy.currentOverride()).isNull()
        assertThatThrownBy { privacy.setOverride(5) }.isInstanceOf(IllegalStateException::class.java)
    }
}
