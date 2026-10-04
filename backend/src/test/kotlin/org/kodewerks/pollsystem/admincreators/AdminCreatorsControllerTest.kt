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
import org.kodewerks.pollsystem.model.PollKind
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
    @Autowired private lateinit var pollBlocks: org.kodewerks.pollsystem.poll.PollBlockService

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
        assertThat(row.manageable).isFalse() // can't edit nationwide access...
        assertThat(row.canToggle).isTrue()   // ...but can disable them within CA
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
    private fun stateDraft(t: String, initial: String) = QuestionnaireDraftRequest(
        pollTypeId = 2L, title = t, summary = "s", questions = listOf(QuestionInput("Q?")),
        scopeLevel = ScopeLevel.STATE, regionIds = listOf(state(initial).id)
    )
    private fun blocksOf(sup: AppUserDetails, email: String) =
        adminPolls.list(sup, null, null, null, null, null, null, true, email).associate { p ->
            p.title to adminPolls.listBlocks(p.type, p.id).map { b -> b.zipcode ?: b.countyName ?: b.stateInitial ?: "EVERYWHERE" }
        }
    /** An ADMIN whose purview is the given zipcodes (like admin@local.test). */
    private fun zipAdmin(vararg zips: String): AppUserDetails {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "mczipadmin")
        zips.forEach { z ->
            val la = county("CA", "Los Angeles")
            roleAssignments.save(RoleAssignment(user = admin, role = AccessLevel.ADMIN, scopeLevel = ScopeLevel.ZIP,
                state = la.state, county = la, zipcode = z, enabled = true))
        }
        return AppUserDetails(admin)
    }

    @Test
    fun `disabling blocks only poll ∩ creator ∩ admin purview, and Polls counts enabled ones`() {
        val admin = zipAdmin("90001", "90012")
        val sup = superUser()
        val c = creator("mc-x").also { grant(it, ScopeLevel.STATE, "CA") }
        questionnaires.saveDraft(c, stateDraft("Statewide", "CA"))

        val before = rowOf(controller.list(admin), c)
        assertThat(before.enabled).isTrue()
        assertThat(before.pollCount to before.pollTotal).isEqualTo(1 to 1)

        val off = controller.setEnabled(admin, c.id, SetEnabledRequest(false))
        assertThat(off.enabled).isFalse()
        assertThat(off.pollCount to off.pollTotal).isEqualTo(0 to 1) // disabled here, still listed
        // A CA-wide poll by a CA creator, disabled by a two-zip admin: just those zips.
        assertThat(blocksOf(sup, c.email)).isEqualTo(mapOf("Statewide" to listOf("90001", "90012")))
        val pollId = adminPolls.list(sup, null, null, null, null, null, null, true, c.email).single().id
        assertThat(pollBlocks.isBlockedFor(PollKind.QUESTIONNAIRE, pollId, "90001")).isTrue()
        assertThat(pollBlocks.isBlockedFor(PollKind.QUESTIONNAIRE, pollId, "94110")).isFalse()
    }

    @Test
    fun `a disabled creator can't create polls in the admin's purview, only elsewhere`() {
        val admin = zipAdmin("90001")
        val c = creator("mc-guard").also { grant(it, ScopeLevel.STATE, "CA") }
        controller.setEnabled(admin, c.id, SetEnabledRequest(false))

        assertStatus(HttpStatus.FORBIDDEN) { questionnaires.saveDraft(c, draft("Here", "90001")) }
        assertStatus(HttpStatus.FORBIDDEN) { questionnaires.saveDraft(c, stateDraft("Statewide", "CA")) }
        questionnaires.saveDraft(c, draft("Elsewhere", "94110"))

        controller.setEnabled(admin, c.id, SetEnabledRequest(true))
        questionnaires.saveDraft(c, draft("Here again", "90001"))
    }

    @Test
    fun `re-enabling one poll keeps the creator disabled, re-checking the creator lifts their blocks`() {
        val admin = zipAdmin("90001")
        val sup = superUser()
        val c = creator("mc-stored").also { grant(it, ScopeLevel.STATE, "CA") }
        questionnaires.saveDraft(c, draft("One", "90001"))
        questionnaires.saveDraft(c, draft("Two", "90001"))
        controller.setEnabled(admin, c.id, SetEnabledRequest(false))

        // Re-enable "One" from Manage Polls.
        val one = adminPolls.list(admin, null, "One", null, null, null, null, true, c.email).single()
        adminPolls.listBlocks(one.type, one.id).forEach { adminPolls.deleteBlock(it.id, admin) }
        val row = rowOf(controller.list(admin), c)
        assertThat(row.enabled).isFalse()
        assertThat(row.pollCount to row.pollTotal).isEqualTo(1 to 2)

        val on = controller.setEnabled(admin, c.id, SetEnabledRequest(true))
        assertThat(on.enabled).isTrue()
        assertThat(on.pollCount).isEqualTo(2)
        assertThat(blocksOf(sup, c.email).values.flatten()).isEmpty()
    }

    @Test
    fun `a block outside the admin's purview neither counts against them nor is lifted by them`() {
        val admin = caAdmin()
        val sup = superUser()
        val c = creator("mc-mixed").also { grant(it, ScopeLevel.STATE, "CA") }
        val held = questionnaires.saveDraft(c, draft("Held", "90001"))
        // A super blocks "Held" for New York submitters: outside a CA admin's purview.
        adminPolls.createBlock("QUESTIONNAIRE", held.id, CreateBlockRequest(BlockScope.STATE, null, null, state("NY").id), sup)
        assertThat(rowOf(controller.list(admin), c).pollCount).isEqualTo(1)

        controller.setEnabled(admin, c.id, SetEnabledRequest(false))
        controller.setEnabled(admin, c.id, SetEnabledRequest(true))
        assertThat(blocksOf(sup, c.email)).isEqualTo(mapOf("Held" to listOf("NY")))
    }

    @Test
    fun `a creator with polls but no access in the purview is listed but can't be toggled`() {
        val admin = caAdmin()
        val c = creator("mc-former")
        val g = grant(c, ScopeLevel.STATE, "CA")
        questionnaires.saveDraft(c, draft("Old poll", "90001"))
        roleAssignments.delete(g)

        val row = rowOf(controller.list(admin), c)
        assertThat(row.pollCount).isEqualTo(1)
        assertThat(row.canToggle).isFalse()
        assertStatus(HttpStatus.CONFLICT) { controller.setEnabled(admin, c.id, SetEnabledRequest(false)) }
    }
}
