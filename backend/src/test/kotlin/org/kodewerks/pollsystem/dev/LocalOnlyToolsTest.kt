package org.kodewerks.pollsystem.dev

import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/**
 * Swagger UI, the OpenAPI document and the dev token/seed endpoints exist only
 * under the `local` profile. Any other profile (here `test`, likewise staging /
 * prod) must not serve them, even though SecurityConfig permits the paths.
 */
@AutoConfigureMockMvc
class LocalOnlyToolsTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `swagger ui and the api docs are not served outside the local profile`() {
        mockMvc.get("/v3/api-docs").andExpect { status { isNotFound() } }
        mockMvc.get("/swagger-ui/index.html").andExpect { status { isNotFound() } }
        mockMvc.get("/swagger-ui.html").andExpect { status { isNotFound() } }
    }

    @Test
    fun `dev token and seed endpoints are not served outside the local profile`() {
        mockMvc.post("/api/dev/token") { param("email", "admin@local.test") }.andExpect { status { isNotFound() } }
        mockMvc.post("/api/dev/seed-user").andExpect { status { isNotFound() } }
    }
}
