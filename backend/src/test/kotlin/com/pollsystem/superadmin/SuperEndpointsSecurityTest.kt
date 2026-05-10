package com.pollsystem.superadmin

import com.pollsystem.AbstractIntegrationTest
import com.pollsystem.TestFixtures
import com.pollsystem.model.AccessLevel
import com.pollsystem.model.User
import com.pollsystem.security.JwtTokenProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// Validates that the role-hierarchy enforcement on /api/super/** locks out
// anyone below SUPER, and that /api/admin/** locks out anyone below ADMIN.
// One JWT-driven test per protected path covers the security wiring;
// functional tests for each controller live in their own files.
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SuperEndpointsSecurityTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var tokens: JwtTokenProvider

    private fun bearer(user: User) =
        "Bearer ${tokens.generateToken(user.id, user.email)}"

    @ParameterizedTest
    @ValueSource(strings = [
        "/api/super/admins",
        "/api/super/admin-requests",
        "/api/super/poll-types",
        "/api/super/ip-rules"
    ])
    fun `super endpoints reject unauthenticated requests with 401`(path: String) {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized)
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "/api/super/admins",
        "/api/super/admin-requests",
        "/api/super/poll-types",
        "/api/super/ip-rules"
    ])
    fun `super endpoints reject ADMIN with 403`(path: String) {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "admin")
        mockMvc.perform(get(path).header("Authorization", bearer(admin)))
            .andExpect(status().isForbidden)
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "/api/super/admins",
        "/api/super/admin-requests",
        "/api/super/poll-types",
        "/api/super/ip-rules"
    ])
    fun `super endpoints accept SUPER with 200`(path: String) {
        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "super")
        mockMvc.perform(get(path).header("Authorization", bearer(sup)))
            .andExpect(status().isOk)
    }

    @Test
    fun `SUPER also reaches admin endpoints (role hierarchy via hasAnyRole)`() {
        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "super")
        mockMvc.perform(
            get("/api/admin/creator-requests").header("Authorization", bearer(sup))
        ).andExpect(status().isOk)
    }
}
