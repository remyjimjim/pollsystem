package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.repository.BallotResponseRepository
import org.kodewerks.pollsystem.repository.CandidateResponseRepository
import org.kodewerks.pollsystem.repository.QuestionResponseRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * How many people answered a poll, and how many of them live inside its
 * purview: the Creator dashboard's "at a glance" figures (creator reports,
 * phase 1).
 *
 * [inArea] is null when showing it would expose a small group: the inside and
 * outside groups must each be empty or at least the k-anonymity threshold,
 * the same rule the results page applies to a narrowed view. [respondents]
 * is always shown, like the unfiltered public results.
 */
data class Participation(val respondents: Int, val inArea: Int?)

@Service
class PollParticipationService(
    private val questionResponses: QuestionResponseRepository,
    private val candidateResponses: CandidateResponseRepository,
    private val ballotResponses: BallotResponseRepository,
    private val purviews: PollPurviewService,
    @Value("\${app.results.k-anonymity-threshold:10}") private val kThreshold: Int
) {
    @Transactional(readOnly = true)
    fun of(kind: PollKind, pollId: Long): Participation {
        // One zipcode per respondent (a respondent answers many questions or
        // candidates, but counts once).
        val zipByUser: Map<Long, String?> = when (kind) {
            PollKind.QUESTIONNAIRE -> questionResponses.findByQuestionnaireId(pollId).associate { it.user.id to it.user.zipcode }
            PollKind.ELECTION -> candidateResponses.findByElectionId(pollId).associate { it.user.id to it.user.zipcode }
            PollKind.BALLOT_MEASURE -> ballotResponses.findByMeasureId(pollId).associate { it.user.id to it.user.zipcode }
        }
        val respondents = zipByUser.size
        if (respondents == 0) return Participation(0, 0)

        val within = purviews.classifyZips(purviews.purviewOf(kind, pollId), zipByUser.values)
        val inArea = zipByUser.values.count { within[it] == true }
        val outside = respondents - inArea
        val safe = (inArea == 0 || inArea >= kThreshold) && (outside == 0 || outside >= kThreshold)
        return Participation(respondents, if (safe) inArea else null)
    }
}
