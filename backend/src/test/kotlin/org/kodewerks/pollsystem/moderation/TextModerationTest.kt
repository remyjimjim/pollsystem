package org.kodewerks.pollsystem.moderation

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put

/**
 * Listed words in text other than comments (decided 2026-11-01): in BLOCK
 * mode (the default) the save is refused with "Please reword …"; candidates'
 * names and a ballot measure's summary aren't refused. WARN mode lets it all
 * through.
 */
@AutoConfigureMockMvc
class TextModerationTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var badWords: BadWords
    @Autowired private lateinit var fixtures: TestFixtures

    private val word by lazy { badWords.list.first { !it.word.contains(' ') }.word }
    private fun creator() = AppUserDetails(fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "txtmod"))

    private fun send(path: String, me: AppUserDetails, body: Any): ResultActionsDsl =
        mockMvc.post(path) { with(user(me)); contentType = MediaType.APPLICATION_JSON; content = json.writeValueAsString(body) }

    private fun ResultActionsDsl.refused(where: String) = andExpect {
        // MockMvc skips Spring's error page, so the message is the status reason here.
        status { isBadRequest(); reason("Please reword “$word” in $where") }
    }

    private fun election(title: String, office: String, name: String = "Ann Lee") = mapOf(
        "pollTypeId" to 1L, "title" to title, "date" to "2030-01-01", "scopeLevel" to "NATIONAL",
        "candidates" to listOf(mapOf("name" to name, "affiliation" to "Indep", "officeName" to office))
    )

    @Test
    fun `the default mode is BLOCK, and the check says so`() {
        assertThat(badWords.textMode()).isEqualTo(TextMode.BLOCK)
        send("/api/moderation/check", creator(), mapOf("texts" to listOf("x"))).andExpect {
            status { isOk() }
            jsonPath("$.textMode") { value("BLOCK") }
        }
    }

    @Test
    fun `questionnaire title, summary and questions are refused, naming where`() {
        val me = creator()
        val q = { title: String, summary: String, q2: String -> mapOf(
            "pollTypeId" to 2L, "title" to title, "summary" to summary, "scopeLevel" to "NATIONAL",
            "questions" to listOf(mapOf("text" to "Fine?"), mapOf("text" to q2))
        ) }
        send("/api/polls/questionnaires", me, q("A $word poll", "s", "ok")).refused("the title")
        send("/api/polls/questionnaires", me, q("Title", "So ${word}s", "ok")).refused("the summary")
        send("/api/polls/questionnaires", me, q("Title", "s", "Is it $word?")).refused("question 2")
        send("/api/polls/questionnaires", me, q("Title", "s", "ok")).andExpect { status { isCreated() } }
    }

    @Test
    fun `election title and office are refused, a candidate's name is not`() {
        val me = creator()
        send("/api/polls/elections", me, election("Vote $word", "Mayor")).refused("the title")
        send("/api/polls/elections", me, election("Vote", "$word Mayor")).refused("the office “$word Mayor”")
        send("/api/polls/elections", me, election("Vote", "Mayor", name = "Chris ${word}s")).andExpect { status { isCreated() } }
    }

    @Test
    fun `a ballot measure's title is refused, its summary is not`() {
        val me = creator()
        val id = json.readTree(send("/api/polls/elections", me, election("Measures", "Mayor"))
            .andReturn().response.contentAsString)["id"].asLong()
        val m = { title: String, summary: String -> mapOf(
            "pollTypeId" to 3L, "electionId" to id, "title" to title, "summary" to summary, "effectiveDate" to "2030-01-01"
        ) }
        send("/api/polls/ballot-measures", me, m("No $word", "s")).refused("the title")
        send("/api/polls/ballot-measures", me, m("Measure 1", "Official text: $word")).andExpect { status { isCreated() } }
    }

    @Test
    fun `a creator request's reason is refused`() {
        val me = AppUserDetails(fixtures.createUser(emailPrefix = "txtmodreq"))
        send("/api/creator-requests", me, mapOf("pollTypeIds" to listOf(2L), "scopeLevel" to "NATIONAL", "reason" to "Let me $word"))
            .refused("the reason")
    }

    @Test
    fun `a super switches the mode on the dashboard, in force at once, and only a super can`() {
        val sup = AppUserDetails(fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "txtmodsup"))
        val me = creator()
        val q = mapOf("pollTypeId" to 2L, "title" to "A $word poll", "summary" to "s", "scopeLevel" to "NATIONAL",
            "questions" to listOf(mapOf("text" to "q")))
        mockMvc.put("/api/super/flags/text-mode") { with(user(me)); contentType = MediaType.APPLICATION_JSON; content = """{"mode":"WARN"}""" }
            .andExpect { status { isForbidden() } }
        try {
            mockMvc.put("/api/super/flags/text-mode") { with(user(sup)); contentType = MediaType.APPLICATION_JSON; content = """{"mode":"WARN"}""" }
                .andExpect {
                    status { isOk() }
                    jsonPath("$.textMode") { value("WARN") }
                    jsonPath("$.textModeDefault") { value("BLOCK") }
                }
            send("/api/moderation/check", me, mapOf("texts" to listOf("x"))).andExpect { jsonPath("$.textMode") { value("WARN") } }
            send("/api/polls/questionnaires", me, q).andExpect { status { isCreated() } }
        } finally {
            // The tests share one database: put it back.
            mockMvc.put("/api/super/flags/text-mode") { with(user(sup)); contentType = MediaType.APPLICATION_JSON; content = """{"mode":"BLOCK"}""" }
        }
        send("/api/polls/questionnaires", me, q).refused("the title")
    }

    @Test
    fun `WARN mode refuses nothing`() {
        val warn = BadWords(json, "warn")
        assertThat(warn.textMode()).isEqualTo(TextMode.WARN)
        assertThatCode { warn.requireClean("the title" to "a $word") }.doesNotThrowAnyException()
    }
}
