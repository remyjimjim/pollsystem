package org.kodewerks.pollsystem.moderation

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

/** The list stays on the server: the check returns only words found, and only to signed-in users. */
@AutoConfigureMockMvc
class BadWordsControllerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var badWords: BadWords
    @Autowired private lateinit var fixtures: TestFixtures

    private fun body(vararg texts: String) = objectMapper.writeValueAsString(mapOf("texts" to texts.toList()))

    @Test
    fun `returns only the words found, per text, in order`() {
        val b = badWords.list.first()
        val me = AppUserDetails(fixtures.createUser(emailPrefix = "bw"))
        mockMvc.post("/api/moderation/check") {
            with(user(me)); contentType = MediaType.APPLICATION_JSON
            content = body("All good", "Oh ${b.word.uppercase()}!")
        }.andExpect {
            status { isOk() }
            jsonPath("$.found.length()") { value(2) }
            jsonPath("$.found[0].length()") { value(0) }
            jsonPath("$.found[1].length()") { value(1) }
            jsonPath("$.found[1][0].word") { value(b.word) }
            jsonPath("$.found[1][0].replace") { value(b.replace) }
        }
    }

    @Test
    fun `signed-out visitors can't use it, and the old list endpoint is gone`() {
        mockMvc.post("/api/moderation/check") { contentType = MediaType.APPLICATION_JSON; content = body("x") }
            .andExpect { status { isUnauthorized() } }
        val me = AppUserDetails(fixtures.createUser(emailPrefix = "bw"))
        mockMvc.get("/api/moderation/bad-words") { with(user(me)) }
            .andExpect { status { is4xxClientError() } }
    }

    @Test
    fun `plurals and possessives of a listed word are caught, and such a comment can't be public`() {
        val listed = badWords.list.first { !it.word.contains(' ') }.word
        for (form in listOf(listed, "${listed}s", "${listed}es", "${listed}'s", "${listed.uppercase()}S"))
            assert(badWords.foundIn("well, $form, then").isNotEmpty()) { "not caught: $form" }
        assert(badWords.foundIn("${listed}ed ${listed}ing").isEmpty())       // other forms only if listed
        assert(!badWords.publishable("you ${listed}s", wantsPublic = true))
    }
}
