package org.kodewerks.pollsystem.poll

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.RoleAssignment
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.CountyRepository
import org.kodewerks.pollsystem.repository.PollTypeRepository
import org.kodewerks.pollsystem.repository.RoleAssignmentRepository
import org.kodewerks.pollsystem.repository.StateRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

/** Creator grants (enabled role_assignments) gate poll saves and publishes. */
class CreatorGrantGuardTest : AbstractIntegrationTest() {

    @Autowired private lateinit var questionnaires: QuestionnaireService
    @Autowired private lateinit var elections: ElectionService
    @Autowired private lateinit var ballotMeasures: BallotMeasureService
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var roleAssignments: RoleAssignmentRepository
    @Autowired private lateinit var states: StateRepository
    @Autowired private lateinit var counties: CountyRepository
    @Autowired private lateinit var pollTypes: PollTypeRepository

    private val electionType = 1L
    private val questionnaireType = 2L
    private val ballotType = 3L

    private fun creatorWithoutGrants(): User =
        fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "grant", nationwideCreatorGrant = false)

    private fun grant(
        user: User,
        level: ScopeLevel,
        stateInitial: String? = null,
        countyName: String? = null,
        zipcode: String? = null,
        pollTypeId: Long? = null,
        role: AccessLevel = AccessLevel.CREATOR,
        enabled: Boolean = true
    ): RoleAssignment {
        val state = stateInitial?.let { states.findByInitial(it)!! }
        val county = countyName?.let { n -> counties.findByStateId(state!!.id).first { it.name == n } }
        return roleAssignments.save(
            RoleAssignment(
                user = user, role = role, scopeLevel = level, state = state, county = county,
                zipcode = zipcode, pollType = pollTypeId?.let { pollTypes.findById(it).get() }, enabled = enabled
            )
        )
    }

    private fun questionnaire(
        scope: ScopeLevel, regionIds: List<Long> = emptyList(), zipcodes: List<String> = emptyList(), title: String = "Q"
    ) = QuestionnaireDraftRequest(
        pollTypeId = questionnaireType, title = title, summary = "s",
        questions = listOf(QuestionInput("Q?")), scopeLevel = scope, regionIds = regionIds, zipcodes = zipcodes
    )

    private fun stateId(initial: String) = states.findByInitial(initial)!!.id
    private fun countyId(initial: String, name: String) =
        counties.findByStateId(stateId(initial)).first { it.name == name }.id

    private fun assertForbidden(mentioning: String, block: () -> Unit) {
        assertThatThrownBy(block)
            .isInstanceOf(ResponseStatusException::class.java)
            .satisfies({ assertThat((it as ResponseStatusException).statusCode).isEqualTo(HttpStatus.FORBIDDEN) })
            .hasMessageContaining(mentioning)
    }

    @Test
    fun `a STATE grant covers that state, its counties and its zips`() {
        val c = creatorWithoutGrants()
        grant(c, ScopeLevel.STATE, stateInitial = "CA")

        questionnaires.saveDraft(c, questionnaire(ScopeLevel.STATE, regionIds = listOf(stateId("CA"))))
        questionnaires.saveDraft(c, questionnaire(ScopeLevel.COUNTY, regionIds = listOf(countyId("CA", "Los Angeles"))))
        questionnaires.saveDraft(c, questionnaire(ScopeLevel.ZIP, zipcodes = listOf("90001")))
    }

    @Test
    fun `regions outside the grants are rejected and named`() {
        val c = creatorWithoutGrants()
        grant(c, ScopeLevel.STATE, stateInitial = "CA")

        assertForbidden("New York") {
            questionnaires.saveDraft(c, questionnaire(ScopeLevel.STATE, regionIds = listOf(stateId("CA"), stateId("NY"))))
        }
        assertForbidden("10001") {
            questionnaires.saveDraft(c, questionnaire(ScopeLevel.ZIP, zipcodes = listOf("90001", "10001")))
        }
        assertForbidden("Nationwide") { questionnaires.saveDraft(c, questionnaire(ScopeLevel.NATIONAL)) }
    }

    @Test
    fun `a COUNTY grant covers its zips but not the rest of the state`() {
        val c = creatorWithoutGrants()
        grant(c, ScopeLevel.COUNTY, stateInitial = "CA", countyName = "Los Angeles")

        questionnaires.saveDraft(c, questionnaire(ScopeLevel.ZIP, zipcodes = listOf("90001")))
        assertForbidden("California") {
            questionnaires.saveDraft(c, questionnaire(ScopeLevel.STATE, regionIds = listOf(stateId("CA"))))
        }
    }

    @Test
    fun `disabled grants and grants for another poll type do not count`() {
        val c = creatorWithoutGrants()
        grant(c, ScopeLevel.NATIONAL, enabled = false)
        grant(c, ScopeLevel.NATIONAL, pollTypeId = electionType)

        assertForbidden("Nationwide") { questionnaires.saveDraft(c, questionnaire(ScopeLevel.NATIONAL)) }

        grant(c, ScopeLevel.NATIONAL, pollTypeId = questionnaireType)
        questionnaires.saveDraft(c, questionnaire(ScopeLevel.NATIONAL))
    }

    @Test
    fun `update is checked too`() {
        val c = creatorWithoutGrants()
        grant(c, ScopeLevel.STATE, stateInitial = "CA")
        val draft = questionnaires.saveDraft(c, questionnaire(ScopeLevel.ZIP, zipcodes = listOf("90001")))

        assertForbidden("10001") {
            questionnaires.update(draft.id, c, questionnaire(ScopeLevel.ZIP, zipcodes = listOf("10001")))
        }
    }

    @Test
    fun `publish is rejected once the covering grant has been disabled`() {
        val c = creatorWithoutGrants()
        val g = grant(c, ScopeLevel.STATE, stateInitial = "CA")
        val draft = questionnaires.saveDraft(c, questionnaire(ScopeLevel.ZIP, zipcodes = listOf("90001")))

        roleAssignments.save(g.copy(enabled = false))

        assertForbidden("90001") { questionnaires.publish(draft.id, c, confirmed = true) }
    }

    @Test
    fun `elections and their ballot measures are checked against the election purview`() {
        val c = creatorWithoutGrants()
        grant(c, ScopeLevel.STATE, stateInitial = "CA", pollTypeId = electionType)

        val election = elections.saveDraft(
            c, ElectionDraftRequest(
                pollTypeId = electionType, title = "E", date = LocalDate.now(),
                scopeLevel = ScopeLevel.STATE, regionIds = listOf(stateId("CA")), candidates = emptyList()
            )
        )
        val measure = BallotMeasureDraftRequest(
            pollTypeId = ballotType, electionId = election.id, title = "M", summary = "s",
            effectiveDate = LocalDate.now()
        )
        // The election grant is type-specific, so the ballot measure isn't covered yet.
        assertForbidden("California") { ballotMeasures.saveDraft(c, measure) }

        grant(c, ScopeLevel.STATE, stateInitial = "CA", pollTypeId = ballotType)
        ballotMeasures.saveDraft(c, measure)
    }

    @Test
    fun `an admin's own admin grants count, and SUPER is unrestricted`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "grantadmin")
        fixtures.assignAdmin(admin) // ZIP-level: CA / Los Angeles / 90001
        questionnaires.saveDraft(admin, questionnaire(ScopeLevel.ZIP, zipcodes = listOf("90001")))
        assertForbidden("10001") {
            questionnaires.saveDraft(admin, questionnaire(ScopeLevel.ZIP, zipcodes = listOf("10001")))
        }

        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "grantsuper")
        questionnaires.saveDraft(sup, questionnaire(ScopeLevel.NATIONAL))
    }
}
