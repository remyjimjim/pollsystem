package org.kodewerks.pollsystem.admincreators

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.adminpolls.AdminPollsController
import org.kodewerks.pollsystem.adminpolls.CreateBlockRequest
import org.kodewerks.pollsystem.model.BlockScope
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.CreatorRequest
import org.kodewerks.pollsystem.model.RequestStatus
import org.kodewerks.pollsystem.model.RoleAssignment
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.poll.QuestionInput
import org.kodewerks.pollsystem.poll.QuestionnaireDraftRequest
import org.kodewerks.pollsystem.poll.QuestionnaireService
import org.kodewerks.pollsystem.repository.CountyRepository
import org.kodewerks.pollsystem.repository.CreatorRequestRepository
import org.kodewerks.pollsystem.repository.RoleAssignmentRepository
import org.kodewerks.pollsystem.repository.StateRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

class AdminCreatorsControllerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var controller: AdminCreatorsController
    @Autowired private lateinit var adminPolls: AdminPollsController
    @Autowired private lateinit var questionnaires: QuestionnaireService
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var roleAssignments: RoleAssignmentRepository
    @Autowired private lateinit var creatorRequests: CreatorRequestRepository
    @Autowired private lateinit var states: StateRepository
    @Autowired private lateinit var counties: CountyRepository

    private fun state(initial: String) = states.findByInitial(initial)!!
    private fun county(initial: String, name: String) = counties.findByStateId(state(initial).id).first { it.name == name }

    private fun creator(prefix: String): User =
        fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = prefix, nationwideCreatorGrant = false)

    private fun grant(
        user: User, level: ScopeLevel, stateInitial: String? = null, countyName: String? = null,
        role: AccessLevel = AccessLevel.CREATOR, enabled: Boolean = true, request: CreatorRequest? = null
    ): RoleAssignment {
        val st = stateInitial?.let { state(it) }
        val co = countyName?.let { county(stateInitial!!, it) }
        return roleAssignments.save(
            RoleAssignment(user = user, role = role, scopeLevel = level, state = st, county = co, enabled = enabled, creatorRequest = request)
        )
    }

    /** An ADMIN whose purview is the state of California. */
    private fun caAdmin(): AppUserDetails {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "mcadmin")
        grant(admin, ScopeLevel.STATE, "CA", role = AccessLevel.ADMIN)
        return AppUserDetails(admin)
    }
    private fun superUser() = AppUserDetails(fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "mcsuper"))

    private fun rowOf(rows: List<CreatorRow>, u: User) = rows.single { it.userId == u.id }

    private fun assertStatus(status: HttpStatus, block: () -> Unit) {
        assertThatThrownBy(block).isInstanceOf(ResponseStatusException::class.java)
            .satisfies({ assertThat((it as ResponseStatusException).statusCode).isEqualTo(status) })
    }

    @Test
    fun `an admin sees creators overlapping their purview, only approved or direct grants`() {
        val admin = caAdmin()
        val inCa = creator("mc-ca").also { grant(it, ScopeLevel.STATE, "CA"); grant(it, ScopeLevel.STATE, "NY") }
        val inLa = creator("mc-la").also { grant(it, ScopeLevel.COUNTY, "CA", "Los Angeles") }
        val inTx = creator("mc-tx").also { grant(it, ScopeLevel.STATE, "TX") }
        val pending = creator("mc-pending").also {
            val req = creatorRequests.save(CreatorRequest(user = it, reason = "r", status = RequestStatus.PENDING))
            grant(it, ScopeLevel.STATE, "CA", enabled = false, request = req)
        }

        val rows = controller.list(admin)
        val ids = rows.map { it.userId }
        assertThat(ids).contains(inCa.id, inLa.id).doesNotContain(inTx.id, pending.id)

        val ca = rowOf(rows, inCa)
        assertThat(ca.accessState).isEqualTo(EnabledState.ENABLED)
        assertThat(ca.manageable).isTrue()
        // NY doesn't overlap a CA admin, so it isn't shown to them.
        assertThat(ca.grants.map { it.stateInitial }).containsExactly("CA")
    }

    @Test
    fun `disabling a grant inside the purview leaves the creator's access partial overall`() {
        val admin = caAdmin()
        val c = creator("mc-partial").also { grant(it, ScopeLevel.STATE, "CA"); grant(it, ScopeLevel.STATE, "NY") }
        val caGrant = rowOf(controller.list(admin), c).grants.single()

        val row = controller.setGrantEnabled(admin, c.id, caGrant.id, SetEnabledRequest(false))
        assertThat(row.accessState).isEqualTo(EnabledState.DISABLED)

        val all = rowOf(controller.list(superUser()), c)
        assertThat(all.accessState).isEqualTo(EnabledState.PARTIAL)
        assertThat(all.grants.associate { it.stateInitial to it.enabled }).isEqualTo(mapOf("CA" to false, "NY" to true))
    }

    @Test
    fun `a creator with no grant inside the purview is visible but their access isn't manageable`() {
        val admin = caAdmin()
        val national = creator("mc-national").also { grant(it, ScopeLevel.NATIONAL) }

        val row = rowOf(controller.list(admin), national)
        assertThat(row.manageable).isFalse()
        assertThat(row.pollsState).isEqualTo(PollsState.NONE)
        assertStatus(HttpStatus.FORBIDDEN) {
            controller.setGrantEnabled(admin, national.id, row.grants.single().id, SetEnabledRequest(false))
        }
    }

    @Test
    fun `admins add grants inside their purview only, and remove only grants they added`() {
        val admin = caAdmin()
        val c = creator("mc-add").also { grant(it, ScopeLevel.COUNTY, "CA", "Los Angeles") }

        val row = controller.addGrants(admin, c.id, AddGrantsRequest(ScopeLevel.COUNTY, regionIds = listOf(county("CA", "Orange").id)))
        assertThat(row.grants.map { it.countyName }).contains("Los Angeles", "Orange")

        assertStatus(HttpStatus.FORBIDDEN) {
            controller.addGrants(admin, c.id, AddGrantsRequest(ScopeLevel.STATE, regionIds = listOf(state("NY").id)))
        }
        assertStatus(HttpStatus.FORBIDDEN) { controller.addGrants(admin, c.id, AddGrantsRequest(ScopeLevel.NATIONAL)) }

        val orange = row.grants.single { it.countyName == "Orange" }
        assertThat(controller.removeGrant(admin, c.id, orange.id).grants.map { it.countyName }).containsExactly("Los Angeles")

        val req = creatorRequests.save(CreatorRequest(user = c, reason = "r", status = RequestStatus.APPROVED))
        val fromRequest = grant(c, ScopeLevel.COUNTY, "CA", "Orange", request = req)
        assertStatus(HttpStatus.CONFLICT) { controller.removeGrant(admin, c.id, fromRequest.id) }
        // ...but it can be disabled.
        val after = controller.setGrantEnabled(admin, c.id, fromRequest.id, SetEnabledRequest(false))
        assertThat(after.accessState).isEqualTo(EnabledState.PARTIAL)
    }

    @Test
    fun `rows carry poll count and last creator edit, and the polls list filters by creator email`() {
        val sup = superUser()
        val c = creator("mc-stats").also { grant(it, ScopeLevel.STATE, "CA") }
        val other = creator("mc-other").also { grant(it, ScopeLevel.STATE, "CA") }
        fun draft(t: String) = QuestionnaireDraftRequest(
            pollTypeId = 2L, title = t, summary = "s", questions = listOf(QuestionInput("Q?")), zipcodes = listOf("90001")
        )
        questionnaires.saveDraft(c, draft("mine 1"))
        questionnaires.saveDraft(c, draft("mine 2"))
        questionnaires.saveDraft(other, draft("theirs"))

        val row = rowOf(controller.list(sup), c)
        assertThat(row.pollCount).isEqualTo(2)
        assertThat(row.lastEditedAt).isNotNull()

        val polls = adminPolls.list(sup, null, null, null, null, null, null, true, c.email.uppercase())
        assertThat(polls.map { it.title }).containsExactlyInAnyOrder("mine 1", "mine 2")
    }

    private fun draft(t: String, zip: String) = QuestionnaireDraftRequest(
        pollTypeId = 2L, title = t, summary = "s", questions = listOf(QuestionInput("Q?")), zipcodes = listOf(zip)
    )
    private fun blockedByTitle(sup: AppUserDetails, email: String) =
        adminPolls.list(sup, null, null, null, null, null, null, true, email).associate { it.title to it.blocked }

    @Test
    fun `Enabled and Polls cover only the creator's polls inside the purview`() {
        val admin = caAdmin()
        val sup = superUser()
        val c = creator("mc-polls").also { grant(it, ScopeLevel.STATE, "CA"); grant(it, ScopeLevel.STATE, "NY") }
        questionnaires.saveDraft(c, draft("LA one", "90001"))
        questionnaires.saveDraft(c, draft("LA two", "90001"))
        questionnaires.saveDraft(c, draft("NYC", "10001"))

        val row = rowOf(controller.list(admin), c)
        assertThat(row.pollCount).isEqualTo(2)
        assertThat(row.pollsState).isEqualTo(PollsState.ENABLED)

        val off = controller.setPollsEnabled(admin, c.id, SetEnabledRequest(false))
        assertThat(off.pollsState).isEqualTo(PollsState.DISABLED)
        assertThat(off.pollCount).isEqualTo(2) // disabled polls still counted and listed
        assertThat(blockedByTitle(sup, c.email)).isEqualTo(mapOf("LA one" to true, "LA two" to true, "NYC" to false))

        val on = controller.setPollsEnabled(admin, c.id, SetEnabledRequest(true))
        assertThat(on.pollsState).isEqualTo(PollsState.ENABLED)
        assertThat(blockedByTitle(sup, c.email).values).containsOnly(false)
    }

    @Test
    fun `a block outside the admin's purview neither shows as disabled to them nor is lifted by them`() {
        val admin = caAdmin()
        val sup = superUser()
        val c = creator("mc-mixed").also { grant(it, ScopeLevel.STATE, "CA") }
        val held = questionnaires.saveDraft(c, draft("Held", "90001"))

        // A super blocks "Held" for New York submitters: outside a CA admin's purview.
        adminPolls.createBlock("QUESTIONNAIRE", held.id, CreateBlockRequest(BlockScope.STATE, null, null, state("NY").id), sup)
        assertThat(rowOf(controller.list(admin), c).pollsState).isEqualTo(PollsState.ENABLED)

        controller.setPollsEnabled(admin, c.id, SetEnabledRequest(false))
        controller.setPollsEnabled(admin, c.id, SetEnabledRequest(true))
        // The CA admin's own disable came and went; the NY block is still there.
        assertThat(blockedByTitle(sup, c.email)).isEqualTo(mapOf("Held" to true))
    }

    @Test
    fun `a creator stays listed for their polls after their access there is removed`() {
        val admin = caAdmin()
        val c = creator("mc-former")
        val g = grant(c, ScopeLevel.STATE, "CA")
        questionnaires.saveDraft(c, draft("Old poll", "90001"))
        roleAssignments.delete(g)

        val row = rowOf(controller.list(admin), c)
        assertThat(row.pollCount).isEqualTo(1)
        assertThat(row.grants).isEmpty()
        assertThat(row.manageable).isFalse()
    }
}
