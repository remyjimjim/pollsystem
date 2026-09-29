package org.kodewerks.pollsystem.adminrequest

import org.kodewerks.pollsystem.model.AdminRequest
import org.kodewerks.pollsystem.model.RequestStatus
import org.kodewerks.pollsystem.model.ScopeLevel
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.Instant

data class SubmitAdminRequest(
    // Requested admin reach: Nationwide / whole state(s) / whole county(ies) / zips.
    val scopeLevel: ScopeLevel = ScopeLevel.STATE,
    val regionIds: List<Long> = emptyList(),   // stateIds for STATE, countyIds for COUNTY
    val zipcodes: List<String> = emptyList(),   // ZIP only
    @field:Size(max = 2000) val reason: String = ""
)

data class AdminRequestDto(
    val id: Long,
    val userId: Long,
    val userEmail: String,
    val status: RequestStatus,
    val reason: String,
    val scopeLevel: ScopeLevel,
    val stateIds: List<Long>,
    val regionLabel: String,
    val zipcodes: List<String>,
    val submittedAt: Instant,
    val processedAt: Instant?,
    val processedByEmail: String?
) {
    companion object {
        fun from(
            req: AdminRequest,
            scopeLevel: ScopeLevel,
            stateIds: List<Long>,
            regionLabel: String,
            zipcodes: List<String>
        ) = AdminRequestDto(
            id = req.id,
            userId = req.user.id,
            userEmail = req.user.email,
            status = req.status,
            reason = req.reason,
            scopeLevel = scopeLevel,
            stateIds = stateIds,
            regionLabel = regionLabel,
            zipcodes = zipcodes,
            submittedAt = req.submittedAt,
            processedAt = req.processedAt,
            processedByEmail = req.processedBy?.email
        )
    }
}

data class AdminBatchDecisionRequest(
    @field:NotEmpty val requestIds: List<Long>
)
