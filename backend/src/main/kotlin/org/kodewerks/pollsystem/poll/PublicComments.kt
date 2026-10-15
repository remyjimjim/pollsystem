package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.repository.BallotResponseRepository
import org.kodewerks.pollsystem.repository.CandidateResponseRepository
import org.kodewerks.pollsystem.repository.QuestionResponseRepository
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Public comments for the results page, anonymous (text only), per item
 * (question, candidate, or the measure itself).
 *
 * A comment shows only if its author's answer is among [answers], the answers
 * the results request may see. So comments follow the same small-poll privacy
 * as the numbers: nothing until k respondents, new ones only with the next
 * step of k, none for a group too small to show, and live for supers.
 */
@Component
class PublicComments(
    private val questionResponses: QuestionResponseRepository,
    private val candidateResponses: CandidateResponseRepository,
    private val ballotResponses: BallotResponseRepository
) {
    private data class Row(val userId: Long, val itemId: Long, val comment: String?, val public: Boolean, val at: Instant)

    fun byItem(kind: PollKind, pollId: Long, answers: List<Answer>): Map<Long, List<String>> {
        if (answers.isEmpty()) return emptyMap()
        val shown = answers.map { it.userId to it.itemId }.toSet()
        val rows = when (kind) {
            PollKind.QUESTIONNAIRE -> questionResponses.findByQuestionnaireId(pollId)
                .map { Row(it.user.id, it.question.id, it.comment, it.commentPublic, it.dateSubmitted) }
            PollKind.ELECTION -> candidateResponses.findByElectionId(pollId)
                .map { Row(it.user.id, it.candidate.id, it.comment, it.commentPublic, it.dateSubmitted) }
            PollKind.BALLOT_MEASURE -> ballotResponses.findByMeasureId(pollId)
                .map { Row(it.user.id, pollId, it.comment, it.commentPublic, it.dateSubmitted) }
        }
        return rows
            .filter { it.public && !it.comment.isNullOrBlank() && (it.userId to it.itemId) in shown }
            .sortedBy { it.at }
            .groupBy({ it.itemId }, { it.comment!!.trim() })
    }
}
