package org.kodewerks.pollsystem.superadmin

import org.kodewerks.pollsystem.repository.UserRepository
import org.kodewerks.pollsystem.substack.StripeSubstackProperties
import org.kodewerks.pollsystem.substack.SubstackResync
import org.kodewerks.pollsystem.substack.SubstackSyncEntry
import org.kodewerks.pollsystem.substack.SubstackSyncLog
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class SubstackStatus(
    val webhookConfigured: Boolean,
    val resyncConfigured: Boolean,
    /** Members whose membership comes from Substack. */
    val members: Int,
    val recent: List<SubstackSyncEntry>
)

/** The Super dashboard's Substack members card (supers only: under /api/super). */
@RestController
@RequestMapping("/api/super/substack")
class SuperSubstackController(
    private val props: StripeSubstackProperties,
    private val resync: SubstackResync,
    private val syncLog: SubstackSyncLog,
    private val users: UserRepository
) {
    @GetMapping
    fun status() = SubstackStatus(props.webhookSecret.isNotBlank(), resync.configured(),
        users.findBySubstackCustomerIdIsNotNull().size, syncLog.recent(50))

    /** Re-sync now; errors come back for the screen (and go to the backend log). */
    @PostMapping("/resync")
    fun resyncNow() = resync.run("resync")
}
