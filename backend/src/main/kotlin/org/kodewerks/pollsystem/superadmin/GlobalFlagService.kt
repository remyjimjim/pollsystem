package org.kodewerks.pollsystem.superadmin

import org.kodewerks.pollsystem.model.AppFlag
import org.kodewerks.pollsystem.repository.AppFlagRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Reads and writes the singleton `app_flags` row. `pollsDisabled` is the
 * Super-admin kill-switch checked by [org.kodewerks.pollsystem.web.PollsDisabledInterceptor]
 * on every /api/polls/… request. The row is created by migration V21; the
 * `orElse(AppFlag())` fallbacks are defensive only.
 */
@Service
class GlobalFlagService(private val flags: AppFlagRepository) {

    @Transactional(readOnly = true)
    fun current(): AppFlag = flags.findById(1).orElse(AppFlag())

    fun pollsDisabled(): Boolean = current().pollsDisabled

    @Transactional
    fun setPollsDisabled(disabled: Boolean, byUserId: Long): AppFlag =
        flags.save(current().copy(pollsDisabled = disabled, updatedAt = Instant.now(), updatedBy = byUserId))
}
