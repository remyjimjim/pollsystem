package org.kodewerks.pollsystem.creatorrequest

import org.kodewerks.pollsystem.model.CreatorRequest
import org.kodewerks.pollsystem.model.RequestStatus
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.Instant

data class SubmitCreatorRequest(
    @field:NotEmpty val pollTypeIds: List<Long>,
    @field:NotEmpty val zipcodes: List<String>,
    @field:Size(max = 2000) val reason: String = ""
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
    val processedAt: Instant?,
    val processedByEmail: String?
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
            processedAt = req.processedAt,
            processedByEmail = req.processedBy?.email
        )
    }
}

data class BatchDecisionRequest(
    @field:NotEmpty val requestIds: List<Long>
)
