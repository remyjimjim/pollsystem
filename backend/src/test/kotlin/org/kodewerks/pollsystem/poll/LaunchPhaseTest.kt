package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.repository.AppFlagRepository
import org.kodewerks.pollsystem.repository.CandidateRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate

/** Getting started (decided 2026-11-03): k = 1 until 100 voters, then the configured 4; groups stay at 4. */
class LaunchPhaseTest : AbstractIntegrationTest() {

    @Autowired private lateinit var flags: AppFlagRepository
    @Autowired private lateinit var env: Environment
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var elections: ElectionService
    @Autowired private lateinit var votes: ElectionResponseController
    @Autowired private lateinit var candidates: CandidateRepository
    @Autowired private lateinit var fixtures: TestFixtures

    private fun privacy(until: Int) = ResultsPrivacy(4, 4, flags, env, launchThreshold = 1, launchUntilVoters = until, jdbc = jdbc)
        .also { it.recountEveryMs = 0 }

    private val poll by lazy {
        val c = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "launch-creator")
        val id = elections.saveDraft(c, ElectionDraftRequest(
            pollTypeId = 1L, title = "Launch ${System.nanoTime()}", date = LocalDate.now().plusDays(10), scopeLevel = ScopeLevel.NATIONAL,
            candidates = listOf(CandidateInput("Ann", "Ind", "Launch office"))
        )).id
        elections.publish(id, c, confirmed = true)
        id
    }
    private fun newVoter() {
        val u = fixtures.createUser(emailPrefix = "launch-voter")
        votes.submit(AppUserDetails(u), poll, SubmitElectionResponsesRequest(candidates.findByElectionId(poll).map { CandidateAnswerInput(it.id, true) }))
    }

    @Test
    fun `k is 1 until the voter target, then the configured 4 once, and narrowed groups stay at 4 throughout`() {
        newVoter()
        val p = privacy(until = 0).let { privacy(until = it.totalVoters() + 1) }
        assertThat(p.k()).isEqualTo(1)
        assertThat(p.groupMinimum()).isEqualTo(4)
        assertThat(p.takeLaunchEnded()).isFalse()

        newVoter()                                   // the target is reached
        assertThat(p.k()).isEqualTo(4)
        assertThat(p.groupMinimum()).isEqualTo(4)
        assertThat(p.takeLaunchEnded()).isTrue()     // the watcher rebuilds the snapshots once
        assertThat(p.takeLaunchEnded()).isFalse()
    }

    @Test
    fun `a server that starts past the target, or with the phase off, uses 4 and rebuilds nothing`() {
        newVoter()
        val past = privacy(until = 1)
        assertThat(past.k()).isEqualTo(4)
        assertThat(past.takeLaunchEnded()).isFalse()
        val off = privacy(until = 0)
        assertThat(off.k()).isEqualTo(4)
        assertThat(off.inLaunchPhase()).isFalse()
    }
}
