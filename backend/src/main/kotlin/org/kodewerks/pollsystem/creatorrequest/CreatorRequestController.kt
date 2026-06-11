package org.kodewerks.pollsystem.creatorrequest

import org.kodewerks.pollsystem.security.AppUserDetails
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/creator-requests")
class CreatorRequestController(private val service: CreatorRequestService) {

    @PostMapping
    fun submit(
        @AuthenticationPrincipal principal: AppUserDetails,
        @Valid @RequestBody body: SubmitCreatorRequest
    ): ResponseEntity<CreatorRequestDto> {
        val saved = service.submit(principal.user, body)
        return ResponseEntity.status(HttpStatus.CREATED).body(service.toDto(saved))
    }

    @GetMapping("/me")
    fun listMine(@AuthenticationPrincipal principal: AppUserDetails): List<CreatorRequestDto> =
        service.listForUser(principal.user.id).map(service::toDto)
}
