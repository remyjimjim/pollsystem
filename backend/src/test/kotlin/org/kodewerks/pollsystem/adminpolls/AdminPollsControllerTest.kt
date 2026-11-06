package org.kodewerks.pollsystem.adminpolls

import org.kodewerks.pollsystem.AbstractIntegrationTest
import org.kodewerks.pollsystem.TestFixtures
import org.kodewerks.pollsystem.email.EmailService
import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.BlockScope
import org.kodewerks.pollsystem.model.Election
import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.model.PollType
import org.kodewerks.pollsystem.repository.ElectionRepository
import org.kodewerks.pollsystem.repository.PollTypeBlockRepository
import org.kodewerks.pollsystem.repository.PollTypeRepository
import org.kodewerks.pollsystem.repository.UserRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate

@Import(AdminPollsControllerTest.RecordingEmailConfig::class)
class AdminPollsControllerTest : AbstractIntegrationTest() {

    @Autowired private lateinit var recordedEmails: RecordingEmailService

    @Autowired private lateinit var controller: AdminPollsController
    @Autowired private lateinit var fixtures: TestFixtures
    @Autowired private lateinit var users: UserRepository
    @Autowired private lateinit var elections: ElectionRepository
    @Autowired private lateinit var ballotMeasures: org.kodewerks.pollsystem.repository.BallotMeasureRepository
    @Autowired private lateinit var pollTypes: PollTypeRepository
    @Autowired private lateinit var blocks: PollTypeBlockRepository
    @Autowired private lateinit var pollPurviews: org.kodewerks.pollsystem.repository.PollPurviewRepository
    @Autowired private lateinit var countyZips: org.kodewerks.pollsystem.repository.CountyZipsRepository

    private fun electionPollType(): PollType =
        pollTypes.findAll().first { it.name == "Election" }

    private fun newElection(creator: org.kodewerks.pollsystem.model.User, zipcode: String, title: String): Election {
        val e = elections.save(
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
        // Give the election a realistic COUNTY purview (mirrors how real elections
        // + the V22 backfill populate poll_purviews) so admin geo-matching applies.
        countyZips.findByZipcode(zipcode).firstOrNull()?.county?.let { county ->
            pollPurviews.save(org.kodewerks.pollsystem.model.PollPurview(
                pollType = PollKind.ELECTION, pollId = e.id,
                scopeLevel = org.kodewerks.pollsystem.model.ScopeLevel.COUNTY, countyId = county.id
            ))
        }
        return e
    }

    private fun principalFor(u: org.kodewerks.pollsystem.model.User) = AppUserDetails(u)

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
    fun `block on one poll does not affect a sibling poll at the same zipcode`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "isol-admin")
        fixtures.assignAdmin(admin, "CA", "Los Angeles", "90001")
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "isol-creator")
        val a = newElection(creator, zipcode = "90001", title = "Poll A")
        val b = newElection(creator, zipcode = "90001", title = "Poll B")

        controller.createBlock(
            type = "ELECTION", id = a.id,
            body = CreateBlockRequest(scope = BlockScope.ZIPCODE, zipcode = "90001", countyId = null, stateId = null),
            principal = principalFor(admin)
        )

        val rows = controller.list(principalFor(admin), listOf("ELECTION"), null, null, null, null, null, true)
        val rowA = rows.first { it.id == a.id }
        val rowB = rows.first { it.id == b.id }
        assertThat(rowA.blocked).isTrue()
        assertThat(rowB.blocked).isFalse()
    }

    @Test
    fun `EVERYWHERE block is idempotent, marks the poll blocked, and is removable`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "evw-admin")
        fixtures.assignAdmin(admin, "CA", "Los Angeles", "90001")
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "evw-creator")
        val e = newElection(creator, zipcode = "90001", title = "Everywhere-blockable")

        val first = controller.createBlock(
            type = "ELECTION", id = e.id,
            body = CreateBlockRequest(scope = BlockScope.EVERYWHERE, zipcode = null, countyId = null, stateId = null),
            principal = principalFor(admin)
        )
        assertThat(first.scope).isEqualTo(BlockScope.EVERYWHERE)

        // A second EVERYWHERE request returns the same row (idempotent).
        val second = controller.createBlock(
            type = "ELECTION", id = e.id,
            body = CreateBlockRequest(scope = BlockScope.EVERYWHERE, zipcode = null, countyId = null, stateId = null),
            principal = principalFor(admin)
        )
        assertThat(second.id).isEqualTo(first.id)

        val rows = controller.list(principalFor(admin), listOf("ELECTION"), null, null, null, null, null, true)
        assertThat(rows.first { it.id == e.id }.blocked).isTrue()

        controller.deleteBlock(first.id, principalFor(admin))
        assertThat(blocks.findById(first.id)).isEmpty
    }

    @Test
    fun `EVERYWHERE block on a poll outside the admin's purview is rejected`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "evw-oop-admin")
        fixtures.assignAdmin(admin, "CA", "Los Angeles", "90001")
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "evw-oop-creator")
        val outside = newElection(creator, zipcode = "10001", title = "Outside")

        assertThatThrownBy {
            controller.createBlock(
                type = "ELECTION", id = outside.id,
                body = CreateBlockRequest(scope = BlockScope.EVERYWHERE, zipcode = null, countyId = null, stateId = null),
                principal = principalFor(admin)
            )
        }.isInstanceOfSatisfying(ResponseStatusException::class.java) {
            assertThat(it.statusCode.value()).isEqualTo(403)
        }
    }

    @Test
    fun `note create + list + edit`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "note-admin")
        fixtures.assignAdmin(admin, "CA", "Los Angeles", "90001")
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "note-creator")
        val e = newElection(creator, zipcode = "90001", title = "Notable")

        val created = controller.createNote("ELECTION", e.id, CreateNoteRequest("First note"), principalFor(admin))
        assertThat(created.body).isEqualTo("First note")
        val edited = controller.editNote(created.id, EditNoteRequest("Edited"))
        assertThat(edited.body).isEqualTo("Edited")
        assertThat(controller.listNotes("ELECTION", e.id).first().body).isEqualTo("Edited")
    }

    @Test
    fun `createNote with sendEmail emails the poll creator and persists the flag`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "mail-admin")
        fixtures.assignAdmin(admin, "CA", "Los Angeles", "90001")
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "mail-creator")
        val e = newElection(creator, zipcode = "90001", title = "Mailable")
        recordedEmails.clear()

        val created = controller.createNote(
            "ELECTION", e.id,
            CreateNoteRequest("Heads up about your poll", sendEmail = true),
            principalFor(admin)
        )
        assertThat(created.emailed).isTrue()
        assertThat(recordedEmails.sent).hasSize(1)
        assertThat(recordedEmails.sent[0].to).isEqualTo(creator.email)
        assertThat(recordedEmails.sent[0].body).contains("Heads up")

        // Editing never re-emails.
        recordedEmails.clear()
        controller.editNote(created.id, EditNoteRequest("Edited body"))
        assertThat(recordedEmails.sent).isEmpty()
    }

    @Test
    fun `editPoll changes status, records a reason note, and emails the creator on notify`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "edit-admin")
        fixtures.assignAdmin(admin, "CA", "Los Angeles", "90001")
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "edit-creator")
        val e = newElection(creator, zipcode = "90001", title = "Editable")
        recordedEmails.clear()

        val note = controller.editPoll(
            "ELECTION", e.id,
            AdminPollEditRequest(status = PollStatus.CLOSED, reason = "Closing early for review", notifyCreator = true),
            principalFor(admin)
        )

        assertThat(elections.findById(e.id).get().status).isEqualTo(PollStatus.CLOSED)
        assertThat(note!!.body).contains("status → CLOSED").contains("Closing early for review")
        assertThat(note!!.emailed).isTrue()
        assertThat(recordedEmails.sent).hasSize(1)
        assertThat(recordedEmails.sent[0].to).isEqualTo(creator.email)
    }

    @Test
    fun `editPoll renames a poll, and an election's new close date carries to its ballot measures`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "edit2-admin")
        fixtures.assignAdmin(admin, "CA", "Los Angeles", "90001")
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "edit2-creator")
        val e = newElection(creator, zipcode = "90001", title = "Old name")
        val measureType = pollTypes.findAll().first { it.name == "Referendum/Ballot Measure" }
        val m1 = ballotMeasures.save(org.kodewerks.pollsystem.model.BallotMeasure(creator = creator, pollType = measureType,
            title = "Prop A", summary = "s", election = e, effectiveDate = LocalDate.now(), status = PollStatus.PUBLISHED, closeDate = e.closeDate))
        val m2 = ballotMeasures.save(org.kodewerks.pollsystem.model.BallotMeasure(creator = creator, pollType = measureType,
            title = "Prop B", summary = "s", election = e, effectiveDate = LocalDate.now(), status = PollStatus.PUBLISHED, closeDate = null))
        val newClose = Instant.parse("2026-11-10T04:00:00Z")

        val note = controller.editPoll("ELECTION", e.id,
            AdminPollEditRequest(title = "  New name ", closeDate = newClose, reason = "Typo and extension"), principalFor(admin))

        val saved = elections.findById(e.id).get()
        assertThat(saved.title).isEqualTo("New name")
        assertThat(saved.closeDate).isEqualTo(newClose)
        assertThat(note!!.body).contains("title → \"New name\"").contains("Typo and extension")
        assertThat(ballotMeasures.findById(m1.id).get().closeDate).isEqualTo(newClose)
        assertThat(ballotMeasures.findById(m2.id).get().closeDate).isEqualTo(newClose)

        assertThatThrownBy {
            controller.editPoll("ELECTION", e.id, AdminPollEditRequest(title = "  ", reason = "blank"), principalFor(admin))
        }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Autowired private lateinit var pollEvents: org.kodewerks.pollsystem.repository.PollEventRepository

    @Test
    fun `editPoll takes no reason and leaves it out of the note`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "noreason-admin")
        fixtures.assignAdmin(admin, "CA", "Los Angeles", "90001")
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "noreason-creator")
        val e = newElection(creator, zipcode = "90001", title = "No reason")
        val note = controller.editPoll("ELECTION", e.id, AdminPollEditRequest(title = "Renamed"), principalFor(admin))
        assertThat(note!!.body).isEqualTo("[title → \"Renamed\"]")
        assertThat(elections.findById(e.id).get().title).isEqualTo("Renamed")
    }

    @Test
    fun `editPoll with nothing really changed saves nothing and records no note or History`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "nochange-admin")
        fixtures.assignAdmin(admin, "CA", "Los Angeles", "90001")
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "nochange-creator")
        val e = newElection(creator, zipcode = "90001", title = "Same")
        val before = pollEvents.findByPollTypeAndPollIdOrderByIdDesc(PollKind.ELECTION, e.id).size

        val result = controller.editPoll("ELECTION", e.id,
            AdminPollEditRequest(title = "Same", status = PollStatus.PUBLISHED, closeDate = e.closeDate, reason = "just looking"),
            principalFor(admin))

        assertThat(result).isNull()
        assertThat(controller.listNotes("ELECTION", e.id)).isEmpty()
        assertThat(pollEvents.findByPollTypeAndPollIdOrderByIdDesc(PollKind.ELECTION, e.id)).hasSize(before)

        // One real change among unchanged values records only that change.
        val note = controller.editPoll("ELECTION", e.id,
            AdminPollEditRequest(title = "Same", status = PollStatus.ARCHIVED, closeDate = e.closeDate), principalFor(admin))
        assertThat(note!!.body).isEqualTo("[status → ARCHIVED]")
        assertThat(pollEvents.findByPollTypeAndPollIdOrderByIdDesc(PollKind.ELECTION, e.id).first().details).isEqualTo("Status → ARCHIVED")
    }

    @Test
    fun `setCloseDate gives every listed poll the date, reopens closed ones, records History, and is super-only`() {
        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "scd-super")
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "scd-admin")
        fixtures.assignAdmin(admin, "CA", "Los Angeles", "90001")
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "scd-creator")
        val past = Instant.parse("2026-11-03T20:00:00Z")
        val newDate = Instant.parse("2099-12-01T05:00:00Z")
        val closed = elections.save(newElection(creator, zipcode = "90001", title = "Closed one").copy(closeDate = past))
        val noDate = elections.save(newElection(creator, zipcode = "90001", title = "No date").copy(closeDate = null))
        val already = elections.save(newElection(creator, zipcode = "90001", title = "Already").copy(closeDate = newDate))
        val measureType = pollTypes.findAll().first { it.name == "Referendum/Ballot Measure" }
        val m = ballotMeasures.save(org.kodewerks.pollsystem.model.BallotMeasure(creator = creator, pollType = measureType,
            title = "Prop D", summary = "s", election = closed, effectiveDate = LocalDate.now(), status = PollStatus.PUBLISHED, closeDate = past))
        val refs = listOf(PollRef("ELECTION", closed.id), PollRef("ELECTION", noDate.id), PollRef("ELECTION", already.id))

        assertThatThrownBy { controller.setCloseDate(SetCloseDateRequest(refs, newDate), principalFor(admin)) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) { assertThat(it.statusCode.value()).isEqualTo(403) }

        val result = controller.setCloseDate(SetCloseDateRequest(refs, newDate), principalFor(sup))

        assertThat(result.updated).isEqualTo(3) // closed, noDate, and the measure; "already" is unchanged
        assertThat(result.reopened).isEqualTo(2)
        assertThat(result.closed).isEqualTo(0)
        for (id in listOf(closed.id, noDate.id, already.id)) assertThat(elections.findById(id).get().closeDate).isEqualTo(newDate)
        assertThat(ballotMeasures.findById(m.id).get().closeDate).isEqualTo(newDate)
        val ev = pollEvents.findByPollTypeAndPollIdOrderByIdDesc(PollKind.ELECTION, closed.id).first()
        assertThat(ev.event).isEqualTo(org.kodewerks.pollsystem.model.PollEventType.REOPENED)
        assertThat(ev.details).isEqualTo("Close date: 2026-11-03 20:00 UTC → 2099-12-01 05:00 UTC")
        assertThat(pollEvents.findByPollTypeAndPollIdOrderByIdDesc(PollKind.ELECTION, noDate.id).first().details)
            .isEqualTo("Close date: none → 2099-12-01 05:00 UTC")
        assertThat(pollEvents.findByPollTypeAndPollIdOrderByIdDesc(PollKind.ELECTION, already.id)
            .none { it.details?.startsWith("Close date:") == true }).isTrue()
        assertThat(pollEvents.findByPollTypeAndPollIdOrderByIdDesc(PollKind.BALLOT_MEASURE, m.id).first().details)
            .isEqualTo("Close date: 2026-11-03 20:00 UTC → 2099-12-01 05:00 UTC, with its election")

        // A date already passed closes open polls.
        val passed = Instant.parse("2026-11-05T00:00:00Z")
        val closing = controller.setCloseDate(SetCloseDateRequest(listOf(PollRef("ELECTION", noDate.id)), passed), principalFor(sup))
        assertThat(closing.closed).isEqualTo(1)
        assertThat(elections.findById(noDate.id).get().closeDate).isEqualTo(passed)
    }

    @Test
    fun `removeCloseDates reopens closed polls with their measures, records History, and is super-only`() {
        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "rcd-super")
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "rcd-admin")
        fixtures.assignAdmin(admin, "CA", "Los Angeles", "90001")
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "rcd-creator")
        val past = Instant.parse("2026-11-03T20:00:00Z")
        val closed = elections.save(newElection(creator, zipcode = "90001", title = "Past election").copy(closeDate = past))
        val open = newElection(creator, zipcode = "90001", title = "Future election")
        val measureType = pollTypes.findAll().first { it.name == "Referendum/Ballot Measure" }
        val m = ballotMeasures.save(org.kodewerks.pollsystem.model.BallotMeasure(creator = creator, pollType = measureType,
            title = "Prop C", summary = "s", election = closed, effectiveDate = LocalDate.now(), status = PollStatus.PUBLISHED, closeDate = past))
        val refs = listOf(PollRef("ELECTION", closed.id), PollRef("ELECTION", open.id))

        assertThatThrownBy { controller.removeCloseDates(RemoveCloseDatesRequest(refs), principalFor(admin)) }
            .isInstanceOfSatisfying(ResponseStatusException::class.java) { assertThat(it.statusCode.value()).isEqualTo(403) }
        assertThat(elections.findById(closed.id).get().closeDate).isEqualTo(past)

        val result = controller.removeCloseDates(RemoveCloseDatesRequest(refs), principalFor(sup))

        assertThat(result.cleared).isEqualTo(3)
        assertThat(result.reopened).isEqualTo(2)
        assertThat(elections.findById(closed.id).get().closeDate).isNull()
        assertThat(elections.findById(open.id).get().closeDate).isNull()
        assertThat(ballotMeasures.findById(m.id).get().closeDate).isNull()
        val closedEvent = pollEvents.findByPollTypeAndPollIdOrderByIdDesc(PollKind.ELECTION, closed.id).first()
        assertThat(closedEvent.event).isEqualTo(org.kodewerks.pollsystem.model.PollEventType.REOPENED)
        assertThat(closedEvent.details).isEqualTo("Close date removed (was 2026-11-03 20:00 UTC)")
        assertThat(pollEvents.findByPollTypeAndPollIdOrderByIdDesc(PollKind.ELECTION, open.id).first().event)
            .isEqualTo(org.kodewerks.pollsystem.model.PollEventType.EDITED)
        assertThat(pollEvents.findByPollTypeAndPollIdOrderByIdDesc(PollKind.BALLOT_MEASURE, m.id).first().details)
            .isEqualTo("Close date removed (was 2026-11-03 20:00 UTC), with its election")

        // Run again: nothing left to clear.
        assertThat(controller.removeCloseDates(RemoveCloseDatesRequest(refs), principalFor(sup)).cleared).isEqualTo(0)
    }

    @Test
    fun `editPoll changes a ballot measure's summary, and refuses one for an election or a blank one`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "sum-admin")
        fixtures.assignAdmin(admin, "CA", "Los Angeles", "90001")
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "sum-creator")
        val e = newElection(creator, zipcode = "90001", title = "Summary election")
        val measureType = pollTypes.findAll().first { it.name == "Referendum/Ballot Measure" }
        val m = ballotMeasures.save(org.kodewerks.pollsystem.model.BallotMeasure(creator = creator, pollType = measureType,
            title = "Prop S", summary = "Old text", election = e, effectiveDate = LocalDate.now(), status = PollStatus.PUBLISHED))
        pollPurviews.save(org.kodewerks.pollsystem.model.PollPurview(pollType = PollKind.BALLOT_MEASURE, pollId = m.id,
            scopeLevel = org.kodewerks.pollsystem.model.ScopeLevel.ZIP, zipcode = "90001"))

        val note = controller.editPoll("BALLOT_MEASURE", m.id,
            AdminPollEditRequest(summary = " New text ", reason = "Fixed the wording"), principalFor(admin))
        assertThat(ballotMeasures.findById(m.id).get().summary).isEqualTo("New text")
        assertThat(note!!.body).contains("summary changed")
        assertThat(controller.list(principalFor(admin), listOf("BALLOT_MEASURE"), "Prop S", null, null, null, null, true)
            .single { it.id == m.id }.summary).isEqualTo("New text")

        for (bad in listOf(
            "ELECTION" to AdminPollEditRequest(summary = "x", reason = "r"),
            "BALLOT_MEASURE" to AdminPollEditRequest(summary = "  ", reason = "r"))) {
            val id = if (bad.first == "ELECTION") e.id else m.id
            assertThatThrownBy { controller.editPoll(bad.first, id, bad.second, principalFor(admin)) }
                .isInstanceOf(ResponseStatusException::class.java)
        }
    }

    @Test
    fun `list marks nationwide polls`() {
        val sup = fixtures.createUser(access = AccessLevel.SUPER, emailPrefix = "nat-super")
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "nat-creator")
        val local = newElection(creator, zipcode = "90001", title = "Nat check local ${System.nanoTime()}")
        val national = newElection(creator, zipcode = "90001", title = "Nat check national ${System.nanoTime()}")
        pollPurviews.deleteAll(pollPurviews.findAll().filter { it.pollType == PollKind.ELECTION && it.pollId == national.id })
        pollPurviews.save(org.kodewerks.pollsystem.model.PollPurview(pollType = PollKind.ELECTION, pollId = national.id,
            scopeLevel = org.kodewerks.pollsystem.model.ScopeLevel.NATIONAL))

        val rows = controller.list(principalFor(sup), listOf("ELECTION"), "Nat check", null, null, null, null, true)
            .associate { it.id to it.nationwide }
        assertThat(rows[local.id]).isFalse()
        assertThat(rows[national.id]).isTrue()
    }

    @Test
    fun `editPoll rejects a poll outside the admin's purview`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "editp-admin")
        fixtures.assignAdmin(admin, "CA", "Los Angeles", "90001")
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "editp-creator")
        val outside = newElection(creator, zipcode = "10001", title = "Outside")

        assertThatThrownBy {
            controller.editPoll(
                "ELECTION", outside.id,
                AdminPollEditRequest(status = PollStatus.CLOSED, reason = "nope"),
                principalFor(admin)
            )
        }.isInstanceOfSatisfying(ResponseStatusException::class.java) {
            assertThat(it.statusCode.value()).isEqualTo(403)
        }
    }

    @Test
    fun `list surfaces a coarse poll whose purview overlaps a scoped admin's state, hides one outside`() {
        val admin = fixtures.createUser(access = AccessLevel.ADMIN, emailPrefix = "overlap-admin")
        fixtures.assignAdmin(admin, "CA", "Los Angeles", "90001") // admin's purview covers CA
        val creator = fixtures.createUser(access = AccessLevel.CREATOR, emailPrefix = "overlap-creator")
        val caStateId = countyZips.findByZipcode("90001").first().county.state.id
        val nyStateId = countyZips.findByZipcode("10001").first().county.state.id

        // Coarse STATE-level elections (no single zip), one in CA, one in NY —
        // replace newElection's default COUNTY purview with a clean STATE one.
        val inState = newElection(creator, zipcode = "90001", title = "CA Statewide")
        pollPurviews.deleteByPollTypeAndPollId(PollKind.ELECTION, inState.id)
        pollPurviews.save(org.kodewerks.pollsystem.model.PollPurview(
            pollType = PollKind.ELECTION, pollId = inState.id,
            scopeLevel = org.kodewerks.pollsystem.model.ScopeLevel.STATE, stateId = caStateId
        ))
        val outState = newElection(creator, zipcode = "10001", title = "NY Statewide")
        pollPurviews.deleteByPollTypeAndPollId(PollKind.ELECTION, outState.id)
        pollPurviews.save(org.kodewerks.pollsystem.model.PollPurview(
            pollType = PollKind.ELECTION, pollId = outState.id,
            scopeLevel = org.kodewerks.pollsystem.model.ScopeLevel.STATE, stateId = nyStateId
        ))

        val ids = controller.list(principalFor(admin), listOf("ELECTION"), null, null, null, null, null, false)
            .map { it.id }.toSet()
        assertThat(ids).contains(inState.id)
        assertThat(ids).doesNotContain(outState.id)
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
