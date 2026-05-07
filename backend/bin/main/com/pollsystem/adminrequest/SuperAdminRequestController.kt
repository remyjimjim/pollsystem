package com.pollsystem.adminrequest

import com.pollsystem.security.AppUserDetails
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/super/admin-requests")
class SuperAdminRequestController(private val service: AdminRequestService) {

    @GetMapping
    fun queue(): List<AdminRequestDto> =
        service.listPending().map(service::toDto)

    @PostMapping("/batch-approve")
    fun approve(
        @AuthenticationPrincipal principal: AppUserDetails,
        @Valid @RequestBody body: AdminBatchDecisionRequest
    ): List<AdminRequestDto> =
        service.batchApprove(body.requestIds, principal.user).map(service::toDto)

    @PostMapping("/batch-reject")
    fun reject(
        @AuthenticationPrincipal principal: AppUserDetails,
        @Valid @RequestBody body: AdminBatchDecisionRequest
    ): List<AdminRequestDto> =
        service.batchReject(body.requestIds, principal.user).map(service::toDto)
}
