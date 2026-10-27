package org.kodewerks.pollsystem.superadmin

import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class StartDemoRequest(@field:Pattern(regexp = "^[0-9]{5}$") val zipcode: String)

/** The Super dashboard's Demo account card (supers only: under /api/super). */
@RestController
@RequestMapping("/api/super/demo")
class SuperDemoController(private val demo: DemoAccountService) {
    @GetMapping fun status() = demo.status()

    @PostMapping("/start")
    fun start(@AuthenticationPrincipal principal: AppUserDetails, @Valid @RequestBody body: StartDemoRequest) =
        demo.start(principal.user, body.zipcode)

    @PostMapping("/extend") fun extend() = demo.extend()

    @PostMapping("/end") fun end() = demo.end()
}
