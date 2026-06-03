package com.pollsystem.superadmin

import com.pollsystem.AbstractIntegrationTest
import com.pollsystem.TestFixtures
import com.pollsystem.model.AccessLevel
import com.pollsystem.model.IpRuleType
import com.pollsystem.repository.IpRuleRepository
import com.pollsystem.security.AppUserDetails
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.server.ResponseStatusException

class SuperIpRuleControllerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var controller: SuperIpRuleController
    @Autowired private lateinit var rules: IpRuleRepository
    @Autowired private lateinit var fixtures: TestFixtures

    private fun super_() = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "super")

    @Test
    fun `create persists rule with createdBy populated`() {
        val sup = super_()
        val response = controller.create(
            AppUserDetails(sup),
            IpRuleInput(value = "10.0.0.0/8", type = IpRuleType.ALLOW, note = "internal")
        )

        assertThat(response.statusCode.value()).isEqualTo(201)
        val dto = response.body!!
        assertThat(dto.value).isEqualTo("10.0.0.0/8")
        assertThat(dto.type).isEqualTo(IpRuleType.ALLOW)
        assertThat(dto.note).isEqualTo("internal")
        assertThat(dto.enabled).isTrue
        assertThat(dto.createdByEmail).isEqualTo(sup.email)
    }

    @Test
    fun `create accepts plain IPv4 and rejects garbage`() {
        val sup = super_()

        // Valid IPv4
        controller.create(AppUserDetails(sup), IpRuleInput("192.168.1.1", IpRuleType.DENY))

        // Invalid
        assertThatThrownBy {
            controller.create(AppUserDetails(sup), IpRuleInput("not-an-ip", IpRuleType.DENY))
        }.isInstanceOfSatisfying(ResponseStatusException::class.java) {
            assertThat(it.statusCode.value()).isEqualTo(400)
        }
    }

    @Test
    fun `list returns rules in created-at descending order`() {
        val sup = super_()
        val first = controller.create(AppUserDetails(sup), IpRuleInput("10.0.0.0/8", IpRuleType.ALLOW)).body!!
        Thread.sleep(5)  // ensure distinct timestamps
        val second = controller.create(AppUserDetails(sup), IpRuleInput("10.1.0.0/16", IpRuleType.DENY)).body!!

        val listed = controller.list().filter { it.id == first.id || it.id == second.id }
        assertThat(listed).hasSize(2)
        // Most recently created first
        assertThat(listed[0].id).isEqualTo(second.id)
        assertThat(listed[1].id).isEqualTo(first.id)
    }

    @Test
    fun `toggle flips enabled flag`() {
        val sup = super_()
        val created = controller.create(AppUserDetails(sup), IpRuleInput("172.16.0.0/12", IpRuleType.ALLOW)).body!!
        assertThat(created.enabled).isTrue

        val first = controller.toggle(created.id)
        assertThat(first.enabled).isFalse

        val second = controller.toggle(created.id)
        assertThat(second.enabled).isTrue
    }

    @Test
    fun `delete removes the rule`() {
        val sup = super_()
        val created = controller.create(AppUserDetails(sup), IpRuleInput("8.8.8.8", IpRuleType.DENY)).body!!
        assertThat(rules.existsById(created.id)).isTrue

        controller.delete(created.id)
        assertThat(rules.existsById(created.id)).isFalse
    }

    @Test
    fun `delete on unknown id returns 404`() {
        assertThatThrownBy { controller.delete(999_999L) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode.value()).isEqualTo(404)
            }
    }

    @Test
    fun `toggle on unknown id returns 404`() {
        assertThatThrownBy { controller.toggle(999_999L) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) {
                assertThat(it.statusCode.value()).isEqualTo(404)
            }
    }
}
