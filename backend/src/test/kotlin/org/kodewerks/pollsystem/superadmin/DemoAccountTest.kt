package org.kodewerks.pollsystem.superadmin

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.poll.QuestionInput
import org.kodewerks.pollsystem.poll.QuestionnaireDraftRequest
import org.kodewerks.pollsystem.poll.QuestionnaireService
import org.kodewerks.pollsystem.repository.PollEventRepository
import org.kodewerks.pollsystem.repository.QuestionnaireRepository
import org.kodewerks.pollsystem.repository.RoleAssignmentRepository
import org.kodewerks.pollsystem.repository.UserRepository
import org.kodewerks.pollsystem.model.PollKind
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.Instant

/** The demo account (decided 2026-10-28). */
@AutoConfigureMockMvc
class DemoAccountTest : AbstractIntegrationTest() {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var json: ObjectMapper
    @Autowired private lateinit var demo: DemoAccountService
    @Autowired private lateinit var props: DemoProperties
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var grants: RoleAssignmentRepository
    @Autowired private lateinit var questionnaires: QuestionnaireService
    @Autowired private lateinit var questionnaireRepo: QuestionnaireRepository
    @Autowired private lateinit var events: PollEventRepository

    @AfterEach fun off() { demo.end() }

    private fun post(path: String, body: Any) = mockMvc.perform(
        post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))
    )
    private fun accountStatus() = json.readTree(post("/api/auth/status", mapOf("email" to props.email))
        .andReturn().response.contentAsString).get("status").asText()

    @Test
    fun `off, the demo address is unknown and can't sign in`() {
        assertThat(demo.status().on).isFalse()
        assertThat(accountStatus()).isEqualTo("UNKNOWN")
        post("/api/auth/demo-sign-in", mapOf("email" to props.email)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `started, the address signs straight in as a paid member and creator for 12 hours, and can be extended`() {
        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "demo-super")
        val s = demo.start(sup, "98225")
        assertThat(s.on).isTrue()
        assertThat(s.zipcode).isEqualTo("98225")
        assertThat(Duration.between(Instant.now(), s.until)).isBetween(Duration.ofHours(11), Duration.ofHours(12))
        assertThatThrownBy { demo.start(sup, "98225") }.isInstanceOf(ResponseStatusException::class.java)

        assertThat(accountStatus()).isEqualTo("DEMO")
        post("/api/auth/demo-sign-in", mapOf("email" to props.email.uppercase()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token").isNotEmpty)
            .andExpect(jsonPath("$.user.access").value("CREATOR"))
            .andExpect(jsonPath("$.user.demoUntil").isNotEmpty)
        val user = users.findByEmail(props.email)!!
        assertThat(user.hasActiveSubscription).isTrue()
        assertThat(user.profileComplete).isTrue()
        assertThat(grants.findByUserIdAndRole(user.id, AccessLevel.CREATOR).map { it.state?.name }).containsExactly("Washington")

        val extended = demo.extend()
        assertThat(Duration.between(s.until, extended.until)).isEqualTo(Duration.ofHours(12))
    }

    @Test
    fun `ending it archives its polls for the super, deletes it, and its sign-in stops working`() {
        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "demo-super")
        demo.start(sup, "98225")
        val jwt = json.readTree(post("/api/auth/demo-sign-in", mapOf("email" to props.email))
            .andReturn().response.contentAsString).get("token").asText()
        val demoUser = users.findByEmail(props.email)!!
        val draft = questionnaires.saveDraft(demoUser, QuestionnaireDraftRequest(
            pollTypeId = 2L, title = "Demo poll ${System.nanoTime()}", summary = "s", closeDate = null,
            questions = listOf(QuestionInput("Q?")), zipcodes = listOf("98225")))
        questionnaires.publish(draft.id, demoUser, confirmed = true)

        assertThat(demo.end().on).isFalse()
        val poll = questionnaireRepo.findById(draft.id).get()
        assertThat(poll.status).isEqualTo(PollStatus.ARCHIVED)
        assertThat(poll.creator.id).isEqualTo(sup.id)
        assertThat(events.findByPollTypeAndPollIdOrderByIdDesc(PollKind.QUESTIONNAIRE, draft.id).first().details)
            .isEqualTo("The demo account ended")
        assertThat(users.findByEmail(props.email)).isNull()
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer $jwt")).andExpect(status().isUnauthorized)
        post("/api/auth/demo-sign-in", mapOf("email" to props.email)).andExpect(status().isUnauthorized)
    }

    @Test
    fun `once its time runs out it can't sign in, and the sweep deletes it`() {
        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "demo-super")
        demo.start(sup, "98225")
        val u = users.findByEmail(props.email)!!
        users.save(u.copy(demoUntil = Instant.now().minusSeconds(60)))
        post("/api/auth/demo-sign-in", mapOf("email" to props.email)).andExpect(status().isUnauthorized)
        demo.sweep()
        assertThat(users.findByEmail(props.email)).isNull()
        // And Start makes a fresh one.
        assertThat(demo.start(sup, "98225").on).isTrue()
    }
}
