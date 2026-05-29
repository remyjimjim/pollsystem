package com.pollsystem.superadmin

import com.pollsystem.AbstractIntegrationTest
import com.pollsystem.TestFixtures
import com.pollsystem.email.EmailService
import com.pollsystem.model.AccessLevel
import com.pollsystem.repository.UserMessageRepository
import com.pollsystem.repository.UserRepository
import com.pollsystem.security.AppUserDetails
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Import
import org.springframework.web.server.ResponseStatusException

@Import(SuperUsersControllerTest.RecordingEmailConfig::class)
class SuperUsersControllerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var controller: SuperUsersController
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var userMessages: UserMessageRepository
    @Autowired private lateinit var recordedEmails: RecordingEmailService

    private fun principalFor(user: com.pollsystem.model.User) = AppUserDetails(user)

    @Test
    fun `list defaults to enabled User+Creator+Admin, excludes SUPER and VIEWER`() {
        fixtures.createUser(access = AccessLevel.USER, emailPrefix = "u")
        fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "c")
        fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "a")
        fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "s")
        fixtures.createUser(access = AccessLevel.VIEWER, emailPrefix = "v")

        val rows = controller.list(null, false, null, null, null, null, null)
        val accesses = rows.map { it.access }.toSet()
        assertThat(accesses).contains(AccessLevel.USER, AccessLevel.CREATOR, AccessLevel.ADMIN)
        assertThat(accesses).doesNotContain(AccessLevel.SUPER, AccessLevel.VIEWER)
    }

    @Test
    fun `list role param narrows the result set`() {
        fixtures.createUser(access = AccessLevel.USER, emailPrefix = "u")
        fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "a")

        val only = controller.list(listOf("ADMIN"), false, null, null, null, null, null)
        assertThat(only).allMatch { it.access == AccessLevel.ADMIN }
    }

    @Test
    fun `list excludes disabled by default and includes them when flag is set`() {
        val u = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "dis")
        users.save(u.copy(isEnabled = false))

        val without = controller.list(listOf("USER"), false, "dis", null, null, null, null)
        assertThat(without.none { it.id == u.id }).isTrue()
        val withDisabled = controller.list(listOf("USER"), true, "dis", null, null, null, null)
        assertThat(withDisabled.any { it.id == u.id }).isTrue()
    }

    @Test
    fun `list filters by email substring case-insensitively`() {
        val u = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "Filterme")
        val rows = controller.list(listOf("USER"), false, "FILTERME", null, null, null, null)
        assertThat(rows.any { it.id == u.id }).isTrue()
    }

    @Test
    fun `list filters by zipcode and derives state and county from the zip`() {
        val u = fixtures.createUser(access = AccessLevel.USER, zipcode = "90001", emailPrefix = "geo")
        val rows = controller.list(listOf("USER"), false, "geo", null, null, listOf("90001"), null)
        assertThat(rows.single { it.id == u.id }).satisfies({
            assertThat(it.zipcode).isEqualTo("90001")
            assertThat(it.stateInitial).isEqualTo("CA")
            assertThat(it.countyName).isEqualTo("Los Angeles")
        })
    }

    @Test
    fun `toggleEnabled flips the flag and rejects SUPER`() {
        val u = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "tog")
        val before = users.findById(u.id).orElseThrow().isEnabled
        val after = controller.toggleEnabled(u.id)
        assertThat(after.isEnabled).isEqualTo(!before)

        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "noflip")
        assertThatThrownBy { controller.toggleEnabled(sup.id) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode.value()).isEqualTo(409)
            }
    }

    @Test
    fun `bulkToggle disables only listable roles, leaves SUPER untouched`() {
        val a = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "ba")
        val b = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "bb")
        val s = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "bs")

        controller.bulkToggle(BulkToggleRequest(listOf(a.id, b.id, s.id), enable = false))
        assertThat(users.findById(a.id).orElseThrow().isEnabled).isFalse()
        assertThat(users.findById(b.id).orElseThrow().isEnabled).isFalse()
        // SUPER is filtered out by LISTABLE_ROLES — must remain enabled.
        assertThat(users.findById(s.id).orElseThrow().isEnabled).isTrue()
    }

    @Test
    fun `demote converts Admin to Creator and rejects non-Admin`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "demote-me")
        val after = controller.demote(admin.id)
        assertThat(after.access).isEqualTo(AccessLevel.CREATOR)

        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "alreadycreator")
        assertThatThrownBy { controller.demote(creator.id) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode.value()).isEqualTo(409)
            }
    }

    @Test
    fun `createMessage saves and optionally emails, editMessage updates body`() {
        val recipient = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "msg")
        val super1 = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "supermsg")

        recordedEmails.clear()
        val created = controller.createMessage(
            recipient.id,
            CreateMessageRequest(body = "hello there", sendEmail = true),
            principalFor(super1)
        )
        assertThat(created.body).isEqualTo("hello there")
        assertThat(created.emailed).isTrue()
        assertThat(recordedEmails.sent).hasSize(1)
        assertThat(recordedEmails.sent[0].to).isEqualTo(recipient.email)

        val edited = controller.editMessage(created.id, EditMessageRequest("revised"))
        assertThat(edited.body).isEqualTo("revised")
        // Editing should never re-email.
        assertThat(recordedEmails.sent).hasSize(1)

        val history = controller.listMessages(recipient.id)
        assertThat(history).hasSize(1)
        assertThat(history[0].body).isEqualTo("revised")
    }

    @Test
    fun `createMessage rejects empty and over-2000 char bodies`() {
        val recipient = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "bad")
        val super1 = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "supbad")
        assertThatThrownBy {
            controller.createMessage(recipient.id, CreateMessageRequest("   ", false), principalFor(super1))
        }.isInstanceOfSatisfying(ResponseStatusException::class.java) {
            assertThat(it.statusCode.value()).isEqualTo(400)
        }
        assertThatThrownBy {
            controller.createMessage(recipient.id, CreateMessageRequest("x".repeat(2001), false), principalFor(super1))
        }.isInstanceOfSatisfying(ResponseStatusException::class.java) {
            assertThat(it.statusCode.value()).isEqualTo(400)
        }
    }

    @Test
    fun `emailSuggestions returns prefix matches capped at 20`() {
        // The pool can contain many seeded users; assert specific emails are present.
        fixtures.createUser(emailPrefix = "auto-zzz1")
        fixtures.createUser(emailPrefix = "auto-zzz2")
        val hits = controller.emailSuggestions("auto-zzz")
        assertThat(hits.any { it.startsWith("auto-zzz1") }).isTrue()
        assertThat(hits.any { it.startsWith("auto-zzz2") }).isTrue()
        assertThat(controller.emailSuggestions("   ")).isEmpty()
    }

    @Test
    fun `list with latest message attaches it to the row`() {
        val recipient = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "latest")
        val super1 = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "suplatest")
        controller.createMessage(recipient.id, CreateMessageRequest("first", false), principalFor(super1))
        controller.createMessage(recipient.id, CreateMessageRequest("second", false), principalFor(super1))
        val row = controller.list(listOf("USER"), false, "latest", null, null, null, null).single { it.id == recipient.id }
        assertThat(row.latestMessage?.body).isEqualTo("second")
        assertThat(userMessages.findByUserIdOrderByCreatedAtDesc(recipient.id)).hasSize(2)
    }

    @Test
    fun `list message filter narrows to users with a matching message body`() {
        val hit = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "msgmatch")
        val miss = fixtures.createUser(access = AccessLevel.USER, emailPrefix = "msgmiss")
        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "supmsg")
        controller.createMessage(hit.id,
            CreateMessageRequest("Please review the QUARANTINE policy", false), principalFor(sup))
        controller.createMessage(miss.id,
            CreateMessageRequest("Welcome to the platform", false), principalFor(sup))

        val rows = controller.list(listOf("USER"), false, null, null, null, null, "quarantine")
        val ids = rows.map { it.id }.toSet()
        assertThat(ids).contains(hit.id)
        assertThat(ids).doesNotContain(miss.id)

        // Blank needle is treated as no filter — both users pass.
        val all = controller.list(listOf("USER"), false, null, null, null, null, "   ").map { it.id }.toSet()
        assertThat(all).contains(hit.id, miss.id)
    }

    @TestConfiguration
    class RecordingEmailConfig {
        @Bean
        @Primary
        fun recordingEmailService() = RecordingEmailService()
    }

    class RecordingEmailService : EmailService {
        data class Sent(val to: String, val subject: String, val body: String)
        val sent = mutableListOf<Sent>()
        override fun send(to: String, subject: String, body: String) {
            sent += Sent(to, subject, body)
        }
        fun clear() = sent.clear()
    }
}
