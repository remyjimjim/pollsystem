package org.kodewerks.pollsystem.superadmin

import org.kodewerks.pollsystem.model.AppFlag
import org.kodewerks.pollsystem.moderation.BadWords
import org.kodewerks.pollsystem.moderation.TextMode
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * Super-admin global platform flags. Currently the poll kill-switch:
 * `pollsDisabled` takes every public poll endpoint offline (503) via
 * [org.kodewerks.pollsystem.web.PollsDisabledInterceptor]. SUPER-gated by
 * SecurityConfig (the /api/super namespace), and outside /api/polls so it
 * stays reachable while polls are disabled.
 */
data class FlagsDto(
    val pollsDisabled: Boolean,
    val updatedAt: Instant,
    val updatedBy: Long?,
    /** Moderation: the mode in force, and the configured default it falls back to. */
    val textMode: TextMode,
    val textModeDefault: TextMode
)
data class SetPollsDisabledRequest(val disabled: Boolean)
data class SetTextModeRequest(val mode: TextMode)

@RestController
@RequestMapping("/api/super/flags")
class SuperFlagsController(private val flags: GlobalFlagService, private val badWords: BadWords) {

    private fun AppFlag.toDto() = FlagsDto(pollsDisabled, updatedAt, updatedBy, badWords.textMode(), badWords.defaultTextMode)

    @GetMapping
    fun get(): FlagsDto = flags.current().toDto()

    @PutMapping("/polls-disabled")
    fun setPollsDisabled(
        @RequestBody body: SetPollsDisabledRequest,
        @AuthenticationPrincipal principal: AppUserDetails
    ): FlagsDto = flags.setPollsDisabled(body.disabled, principal.user.id).toDto()

    /** Moderation: BLOCK or WARN for listed words outside comments, in force at once. */
    @PutMapping("/text-mode")
    fun setTextMode(
        @RequestBody body: SetTextModeRequest,
        @AuthenticationPrincipal principal: AppUserDetails
    ): FlagsDto = flags.setTextMode(body.mode.name, principal.user.id).toDto()
}
