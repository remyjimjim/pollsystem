package com.pollsystem.creatorrequest

import com.pollsystem.security.AppUserDetails
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/creator-requests")
class AdminCreatorRequestController(private val service: CreatorRequestService) {

    @GetMapping
    fun queue(@AuthenticationPrincipal principal: AppUserDetails): List<CreatorRequestDto> =
        service.listForAdmin(principal.user).map(service::toDto)

    @PostMapping("/batch-approve")
    fun approve(@Valid @RequestBody body: BatchDecisionRequest): List<CreatorRequestDto> =
        service.batchApprove(body.requestIds).map(service::toDto)

    @PostMapping("/batch-reject")
    fun reject(@Valid @RequestBody body: BatchDecisionRequest): List<CreatorRequestDto> =
        service.batchReject(body.requestIds).map(service::toDto)
}
