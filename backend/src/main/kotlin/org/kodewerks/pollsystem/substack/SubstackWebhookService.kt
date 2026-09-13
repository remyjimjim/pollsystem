package org.kodewerks.pollsystem.substack

import org.kodewerks.pollsystem.auth.MagicLinkEmailer
import org.kodewerks.pollsystem.auth.MagicLinkService
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

/**
 * Applies Substack membership events to user state, mirroring the Stripe webhook
 * but with a rolling `paid_until` window (Substack bills externally, so there's
 * no subscription object to refresh from). The access model downstream — the
 * participation gate, VIEWER↔USER demote/promote — is unchanged; only the
 * activation source differs.
 */
@Service
class SubstackWebhookService(
    private val users: UserRepository,
    private val magicLinks: MagicLinkService,
    private val emailer: MagicLinkEmailer,
    private val props: SubstackProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * A Substack subscription started or renewed: extend membership by the
     * configured window. An unknown email is provisioned (email only; phone +
     * zipcode come via complete-profile at first sign-in) and emailed a magic
     * link so they can get in.
     */
    @Transactional
    fun activate(email: String) {
        val normalized = email.lowercase()
        val until = Instant.now().plus(Duration.ofDays(props.membershipDays))
        val existing = users.findByEmail(normalized)
        if (existing != null) {
            // Reactivating a lapsed VIEWER restores USER; USER+ keep their level.
            val access = if (existing.access == AccessLevel.VIEWER) AccessLevel.USER else existing.access
            users.save(existing.copy(paidUntil = until, access = access))
            log.info("Extended Substack membership for {}", normalized)
            return
        }
        val provisioned = users.save(
            User(email = normalized, access = AccessLevel.USER, isEnabled = true, paidUntil = until)
        )
        val rawToken = magicLinks.issueToken(provisioned)
        emailer.send(provisioned, rawToken)
        log.info("Provisioned Substack member {} and emailed a magic link", normalized)
    }

    /**
     * A Substack subscription ended: drop membership. A non-SUPER account is
     * demoted to VIEWER (loses participation and any elevated role, re-applying
     * after re-subscribing), matching the Stripe cancel path. No-op for an
     * unknown email.
     */
    @Transactional
    fun deactivate(email: String) {
        val user = users.findByEmail(email.lowercase()) ?: return
        val demoted = if (user.access == AccessLevel.SUPER) user.access else AccessLevel.VIEWER
        users.save(user.copy(paidUntil = null, access = demoted))
        log.info("Ended Substack membership for {}", email.lowercase())
    }
}
