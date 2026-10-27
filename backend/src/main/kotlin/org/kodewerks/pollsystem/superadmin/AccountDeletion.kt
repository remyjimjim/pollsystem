package org.kodewerks.pollsystem.superadmin

import jakarta.persistence.EntityManager
import org.kodewerks.pollsystem.authz.RoleAuthCache
import org.kodewerks.pollsystem.email.EmailService
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.payment.PaymentProvider
import org.kodewerks.pollsystem.poll.ResultSnapshotService
import org.kodewerks.pollsystem.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException

/** What a deletion did, for the super's confirmation message. */
data class AccountDeletionResult(
    val email: String,
    /** Polls whose results were recounted without this member's answers. */
    val pollsRecounted: Int,
    val subscriptionCancelled: Boolean
)

/**
 * Permanently deletes a member's account and everything that is theirs, for
 * an "email us" deletion request (decided 2026-10-20; members cancel, only a
 * super deletes):
 * - their answers and comments; every poll they answered has its results
 *   snapshot retaken without them (it may drop below the threshold and hide);
 * - their sign-in links, messages, creator/admin requests and grants;
 * - their Stripe subscription, cancelled at once so they're never charged
 *   again (Stripe keeps its own payment records).
 * Records of what they did as staff (who processed a request, who changed a
 * setting or poll) are kept with the person removed. Once it's done, the
 * person is emailed a confirmation (decided 2026-10-22).
 *
 * Refused for supers, for yourself, and for anyone who owns polls, wrote poll
 * notes, blocks or messages, or has switched creators off: other members'
 * answers and moderation depend on those, so they need handling by hand first.
 */
@Service
class AccountDeletion(
    private val users: UserRepository,
    private val jdbc: JdbcTemplate,
    private val payments: PaymentProvider,
    private val snapshots: ResultSnapshotService,
    private val roleAuthCache: RoleAuthCache,
    private val tx: TransactionTemplate,
    private val entityManager: EntityManager,
    private val email: EmailService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun delete(userId: Long, confirmEmail: String, by: User): AccountDeletionResult {
        val user = users.findById(userId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User not found") }
        if (!user.email.equals(confirmEmail.trim(), ignoreCase = true)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "The email typed doesn't match this account")
        }
        if (user.id == by.id) throw ResponseStatusException(HttpStatus.CONFLICT, "You can't delete your own account")
        if (user.access == AccessLevel.SUPER) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Supers can't be deleted here")
        blockers(user.id).takeIf { it.isNotEmpty() }?.let {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Can't delete yet: this account ${it.joinToString("; ")}.")
        }

        // Stop the billing first: if Stripe can't be reached, nothing is deleted
        // and the super can try again, rather than leaving a charging subscription
        // with no account behind it.
        val hadSubscription = !user.stripeSubscriptionId.isNullOrBlank()
        if (hadSubscription) payments.cancelNow(user)

        val recounted = tx.execute { removeEverything(user.id) }!!
        roleAuthCache.invalidateAuthorizations()
        // After the deletion is saved, so a mail problem can't undo or block it
        // (EmailService logs a failed send rather than throwing).
        email.send(user.email, CONFIRMATION_SUBJECT, confirmationBody(hadSubscription))
        log.info("Deleted account {} at the request of its owner, by super {}; {} polls recounted", user.id, by.id, recounted)
        return AccountDeletionResult(user.email, recounted, hadSubscription)
    }

    /**
     * Deletes the demo account when it ends (V42): no email (the address has no
     * inbox), no Stripe. [DemoAccountService] archives and hands over its polls
     * first, so nothing blocks it.
     */
    fun deleteDemo(userId: Long): Int {
        val recounted = tx.execute { removeEverything(userId) }!!
        roleAuthCache.invalidateAuthorizations()
        log.info("Deleted the demo account {}; {} polls recounted", userId, recounted)
        return recounted
    }

    private fun confirmationBody(hadSubscription: Boolean) = buildString {
        appendLine("Your account data from BallotBox.us.com has been deleted per your request.")
        if (hadSubscription) {
            appendLine()
            appendLine("Your membership has been cancelled, and you won't be charged again.")
        }
    }

    private fun count(sql: String, id: Long): Int = jdbc.queryForObject(sql, Int::class.java, id) ?: 0

    private fun blockers(id: Long): List<String> = buildList {
        val owned = jdbc.queryForObject(
            """select (select count(*) from questionnaires where creator_id = ?)
                    + (select count(*) from elections where creator_id = ?)
                    + (select count(*) from ballot_measures where creator_id = ?)""",
            Int::class.java, id, id, id
        ) ?: 0
        if (owned > 0) add("owns $owned poll${if (owned == 1) "" else "s"}")
        val notes = count("select count(*) from poll_notes where author_id = ?", id)
        if (notes > 0) add("wrote $notes poll note${if (notes == 1) "" else "s"}")
        val blocks = count("select count(*) from poll_type_blocks where created_by = ?", id)
        if (blocks > 0) add("created $blocks poll block${if (blocks == 1) "" else "s"}")
        // Deleting the admin would delete these too, quietly switching the creators back on.
        val disables = count("select count(*) from creator_disables where admin_id = ?", id)
        if (disables > 0) add("has switched off $disables creator${if (disables == 1) "" else "s"}")
        val messages = count("select count(*) from user_messages where author_id = ? and user_id <> author_id", id)
        if (messages > 0) add("wrote $messages message${if (messages == 1) "" else "s"} to other members")
    }

    companion object {
        const val CONFIRMATION_SUBJECT = "Your BallotBox account has been deleted"
    }

    /** Deletes the account and its rows; returns how many polls' results were retaken. */
    private fun removeEverything(id: Long): Int {
        // Plain SQL below: write out pending JPA changes first, and forget the
        // loaded entities after, so none of the deleted rows is written back.
        entityManager.flush()
        val answered = buildSet {
            jdbc.queryForList("select distinct q.questionnaire_id from question_responses r join questions q on q.id = r.question_id where r.user_id = ?", Long::class.java, id)
                .forEach { add(PollKind.QUESTIONNAIRE to it) }
            jdbc.queryForList("select distinct c.election_id from candidate_responses r join candidates c on c.id = r.candidate_id where r.user_id = ?", Long::class.java, id)
                .forEach { add(PollKind.ELECTION to it) }
            jdbc.queryForList("select distinct measure_id from ballot_responses where user_id = ?", Long::class.java, id)
                .forEach { add(PollKind.BALLOT_MEASURE to it) }
        }

        // Their own rows.
        listOf(
            "delete from question_responses where user_id = ?",
            "delete from candidate_responses where user_id = ?",
            "delete from ballot_responses where user_id = ?",
            "delete from magic_link_tokens where user_id = ?",
            "delete from role_assignments where user_id = ?",
            "delete from creator_requests where user_id = ?",
            "delete from admin_requests where user_id = ?",
            // Staff history stays, without the person.
            "update creator_requests set processed_by_id = null where processed_by_id = ?",
            "update creator_requests set assigned_admin_id = null where assigned_admin_id = ?",
            "update admin_requests set processed_by_id = null where processed_by_id = ?",
            "update app_flags set updated_by = null where updated_by = ?",
            "update ip_rules set created_by_id = null where created_by_id = ?",
            // The rest goes with the user row: messages to them, snapshot copies
            // of their answers, creator disables (cascade); poll history and
            // grant switch-offs keep the event with no name (set null).
            "delete from users where id = ?"
        ).forEach { jdbc.update(it, id) }
        entityManager.clear()

        answered.forEach { (kind, pollId) -> snapshots.rebuild(kind, pollId) }
        return answered.size
    }
}
