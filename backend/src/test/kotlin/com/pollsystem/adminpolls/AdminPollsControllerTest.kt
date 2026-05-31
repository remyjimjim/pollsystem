package com.pollsystem.adminpolls

import com.pollsystem.AbstractIntegrationTest
import com.pollsystem.TestFixtures
import com.pollsystem.model.AccessLevel
import com.pollsystem.model.BlockScope
import com.pollsystem.model.Election
import com.pollsystem.model.PollKind
import com.pollsystem.model.PollStatus
import com.pollsystem.model.PollType
import com.pollsystem.repository.ElectionRepository
import com.pollsystem.repository.PollTypeBlockRepository
import com.pollsystem.repository.PollTypeRepository
import com.pollsystem.repository.UserRepository
import com.pollsystem.security.AppUserDetails
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate

class AdminPollsControllerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var controller: AdminPollsController
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var elections: ElectionRepository
    @Autowired private lateinit var pollTypes: PollTypeRepository
    @Autowired private lateinit var blocks: PollTypeBlockRepository

    private fun electionPollType(): PollType =
        pollTypes.findAll().first { it.name == "Election" }

    private fun newElection(creator: com.pollsystem.model.User, zipcode: String, title: String): Election =
        elections.save(
            Election(
                creator = creator,
                pollType = electionPollType(),
                title = title,
                date = LocalDate.now(),
                zipcode = zipcode,
                status = PollStatus.PUBLISHED,
                closeDate = Instant.now().plusSeconds(86_400)
            )
        )

    private fun principalFor(u: com.pollsystem.model.User) = AppUserDetails(u)

    @Test
    fun `purview returns assigned states-counties-zips for ADMIN, unrestricted for SUPER`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "purview-admin")
        fixtures.assignAdmin(admin, "CA", "Los Angeles", "90001")
        val view = controller.purview(principalFor(admin))
        assertThat(view.unrestricted).isFalse()
        assertThat(view.states.map { it.initial }).contains("CA")
        assertThat(view.counties.map { it.name }).contains("Los Angeles")
        assertThat(view.zipcodes).contains("90001")

        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "purview-super")
        val supView = controller.purview(principalFor(sup))
        assertThat(supView.unrestricted).isTrue()
    }

    @Test
    fun `list scopes polls to the admin's purview`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "scope-admin")
        fixtures.assignAdmin(admin, "CA", "Los Angeles", "90001")
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "scope-creator")
        val inPurview = newElection(creator, zipcode = "90001", title = "Within Purview")
        // 10001 = New York; outside CA.
        val outOfPurview = newElection(creator, zipcode = "10001", title = "Outside Purview")

        val rows = controller.list(principalFor(admin), listOf("ELECTION"), null, null, null, null, null, false)
        val ids = rows.map { it.id }.toSet()
        assertThat(ids).contains(inPurview.id)
        assertThat(ids).doesNotContain(outOfPurview.id)
    }

    @Test
    fun `block create plus list plus delete, outside-purview block is rejected`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "block-admin")
        fixtures.assignAdmin(admin, "CA", "Los Angeles", "90001")
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "block-creator")
        val e = newElection(creator, zipcode = "90001", title = "Blockable")

        // Inside purview → succeeds.
        val ok = controller.createBlock(
            type = "ELECTION", id = e.id,
            body = CreateBlockRequest(scope = BlockScope.ZIPCODE, zipcode = "90001", countyId = null, stateId = null),
            principal = principalFor(admin)
        )
        assertThat(ok.scope).isEqualTo(BlockScope.ZIPCODE)
        assertThat(controller.listBlocks("ELECTION", e.id).map { it.id }).contains(ok.id)

        // Outside purview → 403.
        val other = newElection(creator, zipcode = "10001", title = "Other")
        assertThatThrownBy {
            controller.createBlock(
                type = "ELECTION", id = other.id,
                body = CreateBlockRequest(scope = BlockScope.ZIPCODE, zipcode = "10001", countyId = null, stateId = null),
                principal = principalFor(admin)
            )
        }.isInstanceOfSatisfying(ResponseStatusException::class.java) {
            assertThat(it.statusCode.value()).isEqualTo(403)
        }

        // Delete restores blocked=false.
        controller.deleteBlock(ok.id, principalFor(admin))
        assertThat(blocks.findById(ok.id)).isEmpty
    }

    @Test
    fun `includeDisabled controls whether blocked polls show in the list`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "incl-admin")
        fixtures.assignAdmin(admin, "CA", "Los Angeles", "90001")
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "incl-creator")
        val e = newElection(creator, zipcode = "90001", title = "Toggleable")
        controller.createBlock(
            type = "ELECTION", id = e.id,
            body = CreateBlockRequest(scope = BlockScope.ZIPCODE, zipcode = "90001", countyId = null, stateId = null),
            principal = principalFor(admin)
        )

        val defaultRows = controller.list(principalFor(admin), listOf("ELECTION"), null, null, null, null, null, false)
        assertThat(defaultRows.map { it.id }).doesNotContain(e.id)

        val withDisabled = controller.list(principalFor(admin), listOf("ELECTION"), null, null, null, null, null, true)
        assertThat(withDisabled.first { it.id == e.id }.blocked).isTrue()
    }

    @Test
    fun `note create + list + edit`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "note-admin")
        fixtures.assignAdmin(admin, "CA", "Los Angeles", "90001")
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "note-creator")
        val e = newElection(creator, zipcode = "90001", title = "Notable")

        val created = controller.createNote("ELECTION", e.id, CreateNoteRequest("First note"), principalFor(admin))
        assertThat(created.body).isEqualTo("First note")
        val edited = controller.editNote(created.id, CreateNoteRequest("Edited"))
        assertThat(edited.body).isEqualTo("Edited")
        assertThat(controller.listNotes("ELECTION", e.id).first().body).isEqualTo("Edited")
    }
}
