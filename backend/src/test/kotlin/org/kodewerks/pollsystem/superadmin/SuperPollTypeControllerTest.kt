package org.kodewerks.pollsystem.superadmin

import com.fasterxml.jackson.databind.ObjectMapper
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.repository.PollTypeRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.server.ResponseStatusException

class SuperPollTypeControllerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var controller: SuperPollTypeController
    @Autowired private lateinit var pollTypes: PollTypeRepository
    @Autowired private lateinit var json: ObjectMapper

    @Test
    fun `list returns the seeded types with parsed templates`() {
        val all = controller.list()
        assertThat(all).hasSize(3)
        // V4 seeds non-empty templates for each
        assertThat(all).allMatch { it.template.isObject && it.template.has("type") }
        assertThat(all.map { it.name })
            .containsExactlyInAnyOrder("Election", "Questionnaire", "Referendum/Ballot Measure")
    }

    @Test
    fun `updateTemplate persists arbitrary JSON and the next list reflects it`() {
        val original = controller.list().first { it.name == "Questionnaire" }
        val newTemplate = json.readTree("""
            {"type": "Questionnaire", "fields": {"foo": {"required": true}}}
        """.trimIndent())

        val updated = controller.updateTemplate(original.id, newTemplate)
        assertThat(updated.template).isEqualTo(newTemplate)

        // Re-fetch to confirm it persisted, not just echoed
        val refreshed = controller.list().first { it.id == original.id }
        assertThat(refreshed.template).isEqualTo(newTemplate)

        // And the underlying entity now stores the JSON
        val entity = pollTypes.findById(original.id).orElseThrow()
        assertThat(entity.templateJson).contains("\"foo\"")
    }

    @Test
    fun `updateTemplate on unknown id returns 404`() {
        val body = json.readTree("""{"type": "X"}""")
        assertThatThrownBy { controller.updateTemplate(999_999L, body) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode.value()).isEqualTo(404)
            }
    }
}
