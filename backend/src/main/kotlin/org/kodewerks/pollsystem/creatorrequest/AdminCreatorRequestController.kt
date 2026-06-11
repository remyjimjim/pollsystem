package org.kodewerks.pollsystem.creatorrequest

import org.kodewerks.pollsystem.security.AppUserDetails
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/admin/creator-requests")
class AdminCreatorRequestController(
    private val service: CreatorRequestService,
    private val requests: org.kodewerks.pollsystem.repository.CreatorRequestRepository
) {

    @GetMapping
    fun queue(@AuthenticationPrincipal principal: AppUserDetails): List<CreatorRequestDto> =
        service.listForAdmin(principal.user).map(service::toDto)

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): CreatorRequestDto {
        val req = requests.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Creator request not found")
        }
        return service.toDto(req)
    }

    @PostMapping("/batch-approve")
    fun approve(
        @AuthenticationPrincipal principal: AppUserDetails,
        @Valid @RequestBody body: BatchDecisionRequest
    ): List<CreatorRequestDto> =
        service.batchApprove(body.requestIds, principal.user).map(service::toDto)

    @PostMapping("/batch-reject")
    fun reject(
        @AuthenticationPrincipal principal: AppUserDetails,
        @Valid @RequestBody body: BatchDecisionRequest
    ): List<CreatorRequestDto> =
        service.batchReject(body.requestIds, principal.user).map(service::toDto)
}
