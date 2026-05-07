package com.pollsystem.adminrequest

import com.pollsystem.model.AdminRequest
import com.pollsystem.model.RequestStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.time.Instant

data class SubmitAdminRequest(
    @field:NotEmpty val zipcodes: List<String>,
    @field:NotBlank @field:Size(max = 2000) val reason: String
)

data class AdminRequestDto(
    val id: Long,
    val userId: Long,
    val userEmail: String,
    val status: RequestStatus,
    val reason: String,
    val zipcodes: List<String>,
    val submittedAt: Instant,
    val processedAt: Instant?,
    val processedByEmail: String?
) {
    companion object {
        fun from(req: AdminRequest, zipcodes: List<String>) = AdminRequestDto(
            id = req.id,
            userId = req.user.id,
            userEmail = req.user.email,
            status = req.status,
            reason = req.reason,
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
