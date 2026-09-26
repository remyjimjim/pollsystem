package org.kodewerks.pollsystem.creatorrequest

import org.kodewerks.pollsystem.model.CreatorRequest
import org.kodewerks.pollsystem.model.RequestStatus
import org.kodewerks.pollsystem.model.ScopeLevel
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.Instant

/**
 * A creator request now expresses its purview at a scope level:
 *   NATIONAL → no regionIds / zipcodes
 *   STATE    → regionIds = stateIds
 *   COUNTY   → regionIds = countyIds
 *   ZIP      → zipcodes (historical form)
 * Level-appropriate validation happens in CreatorRequestService.submit.
 */
data class SubmitCreatorRequest(
    @field:NotEmpty val pollTypeIds: List<Long>,
    val scopeLevel: ScopeLevel = ScopeLevel.ZIP,
    val regionIds: List<Long> = emptyList(),
    val zipcodes: List<String> = emptyList(),
    @field:Size(max = 2000) val reason: String = ""
)

data class CreatorRequestDto(
    val id: Long,
    val userId: Long,
    val userEmail: String,
    val assignedAdminId: Long?,
    val status: RequestStatus,
    val reason: String,
    val scopeLevel: ScopeLevel,
    val stateIds: List<Long>,
    val regionLabel: String,
    val zipcodes: List<String>,
    val pollTypeIds: List<Long>,
    val submittedAt: Instant,
    val processedAt: Instant?,
    val processedByEmail: String?
) {
    companion object {
        fun from(
            req: CreatorRequest,
            scopeLevel: ScopeLevel,
            stateIds: List<Long>,
            regionLabel: String,
            zipcodes: List<String>,
            pollTypeIds: List<Long>
        ) = CreatorRequestDto(
            id = req.id,
            userId = req.user.id,
            userEmail = req.user.email,
            assignedAdminId = req.assignedAdmin?.id,
            status = req.status,
            reason = req.reason,
            scopeLevel = scopeLevel,
            stateIds = stateIds,
            regionLabel = regionLabel,
            zipcodes = zipcodes,
            pollTypeIds = pollTypeIds,
            submittedAt = req.submittedAt,
            processedAt = req.processedAt,
            processedByEmail = req.processedBy?.email
        )
    }
}

data class BatchDecisionRequest(
    @field:NotEmpty val requestIds: List<Long>
)
