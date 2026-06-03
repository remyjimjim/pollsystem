package com.pollsystem.creatorrequest

import com.fasterxml.jackson.databind.ObjectMapper
import com.pollsystem.AbstractIntegrationTest
import com.pollsystem.TestFixtures
import com.pollsystem.model.AccessLevel
import com.pollsystem.security.JwtTokenProvider
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class AdminCreatorRequestsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var tokens: JwtTokenProvider
    @Autowired private lateinit var service: CreatorRequestService

    private fun bearerFor(user: com.pollsystem.model.User) =
        "Bearer ${tokens.generateToken(user.id, user.email)}"

    @Test
    fun `unauthenticated GET admin queue returns 401`() {
        mockMvc.perform(get("/api/admin/creator-requests"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `regular user cannot access admin queue (403)`() {
        val user = fixtures.createUser()
        mockMvc.perform(
            get("/api/admin/creator-requests").header("Authorization", bearerFor(user))
        ).andExpect(status().isForbidden)
    }

    @Test
    fun `admin sees pending requests routed to them`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "admin")
        fixtures.assignAdmin(admin)
        val applicant = fixtures.createUser(emailPrefix = "applicant")

        // Submit a creator request — service handles routing to the only admin
        service.submit(
            applicant,
            SubmitCreatorRequest(
                pollTypeIds = listOf(1L),
                zipcodes = listOf("90001"),
                reason = "Reason"
            )
        )

        mockMvc.perform(
            get("/api/admin/creator-requests").header("Authorization", bearerFor(admin))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].userEmail").value(applicant.email))
            .andExpect(jsonPath("$[0].status").value("PENDING"))
    }

    @Test
    fun `admin can batch-approve over the wire`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "admin")
        fixtures.assignAdmin(admin)
        val applicant = fixtures.createUser(emailPrefix = "applicant")
        val req = service.submit(
            applicant,
            SubmitCreatorRequest(
                pollTypeIds = listOf(1L),
                zipcodes = listOf("90001"),
                reason = "Reason"
            )
        )

        mockMvc.perform(
            post("/api/admin/creator-requests/batch-approve")
                .header("Authorization", bearerFor(admin))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(mapOf("requestIds" to listOf(req.id))))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(req.id))
            .andExpect(jsonPath("$[0].status").value("APPROVED"))
    }
}
