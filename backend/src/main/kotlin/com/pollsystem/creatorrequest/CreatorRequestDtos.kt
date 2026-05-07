package com.pollsystem.creatorrequest

import com.pollsystem.model.CreatorRequest
import com.pollsystem.model.RequestStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.Instant

data class SubmitCreatorRequest(
    @field:NotEmpty val pollTypeIds: List<Long>,
    @field:NotEmpty val zipcodes: List<String>,
    @field:NotBlank @field:Size(max = 2000) val reason: String
)

data class CreatorRequestDto(
    val id: Long,
    val userId: Long,
    val userEmail: String,
    val assignedAdminId: Long?,
    val status: RequestStatus,
    val reason: String,
    val zipcodes: List<String>,
    val pollTypeIds: List<Long>,
    val submittedAt: Instant,
    val processedAt: Instant?
) {
    companion object {
        fun from(
            req: CreatorRequest,
            zipcodes: List<String>,
            pollTypeIds: List<Long>
        ) = CreatorRequestDto(
            id = req.id,
            userId = req.user.id,
            userEmail = req.user.email,
            assignedAdminId = req.assignedAdmin?.id,
            status = req.status,
            reason = req.reason,
            zipcodes = zipcodes,
            pollTypeIds = pollTypeIds,
            submittedAt = req.submittedAt,
            processedAt = req.processedAt
        )
    }
}

data class BatchDecisionRequest(
    @field:NotEmpty val requestIds: List<Long>
)
