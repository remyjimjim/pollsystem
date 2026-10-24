package org.kodewerks.pollsystem.auth

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.repository.UserRepository
import org.kodewerks.pollsystem.superadmin.SuperUsersController
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** One account per inbox (V40 email_key), and accounts paid with the same card flagged for review. */
@AutoConfigureMockMvc
class AccountUniquenessTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var superUsers: SuperUsersController

    private fun register(email: String, phone: String) = mockMvc.perform(
        post("/api/auth/register-checkout").contentType(MediaType.APPLICATION_JSON)
            .content(json.writeValueAsString(mapOf("email" to email, "phone" to phone, "zipcode" to "90001", "ageConfirmed" to true)))
    )

    @Test
    fun `aliases of one inbox are the same account`() {
        val gmail = users.save(fixtures.createUser(emailPrefix = "uniq-g").copy(email = "jane.doe@gmail.com"))
        val proton = users.save(fixtures.createUser(emailPrefix = "uniq-p").copy(email = "jdoe@proton.me"))

        for (alias in listOf("janedoe@gmail.com", "Jane.Doe+polls@googlemail.com", "j.a.n.e.d.o.e@gmail.com"))
            assertThat(users.findByEmail(alias)?.id).`as`(alias).isEqualTo(gmail.id)
        for (alias in listOf("jdoe@protonmail.com", "jdoe+x@pm.me", "JDOE@protonmail.ch"))
            assertThat(users.findByEmail(alias)?.id).`as`(alias).isEqualTo(proton.id)

        // Dots matter outside Gmail, and other domains keep their +tags.
        assertThat(users.findByEmail("j.doe@proton.me")).isNull()
        assertThat(users.existsByEmail("jane.doe@example.com")).isFalse()

        register("Jane.Doe+second@gmail.com", "+1 (555) 444-0001").andExpect(status().isConflict)
        register("jdoe@protonmail.com", "+1 (555) 444-0002").andExpect(status().isConflict)
    }

    @Test
    fun `signing in with an alias finds the account`() {
        users.save(fixtures.createUser(emailPrefix = "uniq-login").copy(email = "sam.smith@gmail.com"))
        mockMvc.perform(
            post("/api/auth/status").contentType(MediaType.APPLICATION_JSON).content("""{"email":"samsmith+bb@gmail.com"}""")
        ).andExpect(status().isOk)
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("ACTIVE"))
    }

    @Test
    fun `accounts paid with the same card are flagged in the super users list`() {
        val a = users.save(fixtures.createUser(emailPrefix = "uniq-carda").copy(cardFingerprint = "fpTEST123"))
        val b = users.save(fixtures.createUser(emailPrefix = "uniq-cardb").copy(cardFingerprint = "fpTEST123"))
        val c = users.save(fixtures.createUser(emailPrefix = "uniq-cardc").copy(cardFingerprint = "fpOTHER"))

        val rows = superUsers.list(null, false, "uniq-card", null, null, null, null).associateBy { it.id }
        assertThat(rows.getValue(a.id).sharedCardWith).containsExactly(b.email)
        assertThat(rows.getValue(b.id).sharedCardWith).containsExactly(a.email)
        assertThat(rows.getValue(c.id).sharedCardWith).isEmpty()
    }
}
