package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.PollKind
import org.kodewerks.pollsystem.model.PollStatus
import org.kodewerks.pollsystem.repository.BallotMeasureRepository
import org.kodewerks.pollsystem.repository.CandidateRepository
import org.kodewerks.pollsystem.repository.ElectionRepository
import org.kodewerks.pollsystem.repository.StateRepository
import org.kodewerks.pollsystem.security.AppUserDetails
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/** The map's parties (decided 2026-11-03). IND covers independents, "No party listed" and non-partisan. */
enum class Party { DEM, REP, IND, OTHER }

/** The party an affiliation names; a fusion label ("Democratic and Working Families") takes the first one. */
fun partyOf(affiliation: String): Party {
    val a = affiliation.lowercase()
    return listOf(
        Party.DEM to Regex("democrat"),
        Party.REP to Regex("republican"),
        Party.IND to Regex("independent|no party|non-?partisan|unaffiliated")
    ).mapNotNull { (p, r) -> r.find(a)?.let { p to it.range.first } }
        .minByOrNull { it.second }?.first ?: Party.OTHER
}

/** A candidate in a district's House race; pct null while the district's results are hidden. */
data class MapCandidateDto(val name: String, val affiliation: String, val party: Party, val pct: Int?, val votes: Int?)

/** A ballot measure's split among one district's voters; null when that group is under the minimum. */
data class MapMeasureSplitDto(val yesPct: Int, val noPct: Int)

data class MapDistrictDto(
    val number: Int,
    val electionId: Long,
    val officeName: String?,
    /** Whether the district's results can be shown (the same rule as its results page). */
    val shown: Boolean,
    /** Strongest first (when shown). */
    val candidates: List<MapCandidateDto>,
    val measure: MapMeasureSplitDto?
)

data class MapMeasureDto(val id: Long, val title: String)

data class StateMapDto(
    val state: String,
    val stateName: String,
    /** A super's view: live, with raw counts. */
    val live: Boolean,
    val measures: List<MapMeasureDto>,
    val districts: List<MapDistrictDto>
)

/**
 * A state's district map (/maps/WA, decided 2026-11-03): each district's U.S.
 * House race, from its district ballot ("2026 Midterm Washington District 2"),
 * or, in an at-large state (one House seat, decided 2026-11-04), from its one
 * ballot ("2026 Midterm Alaska") as district 1. In a ranked race only 1st
 * choices count.
 * and optionally one of the state's ballot measures split by the voters of
 * each district ballot. Every number comes from [ResultsAccess], so it follows
 * the results pages' privacy: hidden under the minimum, stepped for the
 * public, live for supers; a measure's split in a district is withheld when
 * fewer than the group minimum of its voters answered it.
 */
@RestController
@RequestMapping("/api/polls/maps")
class StateMapController(
    private val states: StateRepository,
    private val elections: ElectionRepository,
    private val candidates: CandidateRepository,
    private val measures: BallotMeasureRepository,
    private val access: ResultsAccess
) {
    private val visible = setOf(PollStatus.PUBLISHED, PollStatus.CLOSED)

    @GetMapping("/{state}/results")
    @Transactional(readOnly = true)
    fun results(
        @PathVariable state: String,
        @RequestParam(required = false) measureId: Long?,
        @AuthenticationPrincipal principal: AppUserDetails? = null
    ): StateMapDto {
        val st = states.findByInitial(state.uppercase())
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown state: $state")
        // The launch-content title shapes (scripts/launch-content/load-state.bash).
        val districtTitle = Regex("^2026 Midterm ${Regex.escape(st.name)} District (\\d+)$")
        val shown = elections.findAll().filter { it.status in visible }
        val atLarge = shown.firstOrNull { it.title == "2026 Midterm ${st.name}" }
        val districts = shown.mapNotNull { e -> districtTitle.find(e.title)?.let { it.groupValues[1].toInt() to e } }
            .sortedBy { it.first }
            .ifEmpty { listOfNotNull(atLarge?.let { 1 to it }) }
        val stateMeasures = shown.filter { it.title.startsWith("2026 Midterm · ${st.name}:") || it.id == atLarge?.id }
            .flatMap { measures.findByElectionId(it.id) }
            .filter { it.status in visible }
            .sortedBy { it.id }
        val measure = measureId?.let { id ->
            stateMeasures.find { it.id == id } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Ballot measure not found")
        }
        val m = measure?.let { access.prepare(principal, PollKind.BALLOT_MEASURE, it.id, null, null, null, true, true) }
        var live = false

        val rows = districts.map { (n, e) ->
            val r = access.prepare(principal, PollKind.ELECTION, e.id, null, null, null, true, true)
            live = r.live
            val all = candidates.findByElectionId(e.id)
            val office = (all.firstOrNull { it.office.name.startsWith("U.S. Representative") } ?: all.firstOrNull())?.office
            val race = all.filter { it.office.id == office?.id }
            val ids = race.map { it.id }.toSet()
            val voters = r.answers.filter { it.itemId in ids }.map { it.userId }.distinct().size
            // No votes yet shows "—" (a super's live view would otherwise read 0% / 0%).
            val ok = r.visibility.mode != "HIDDEN" && !r.suppressed && voters > 0
            val list = race.map { c ->
                val yes = r.answers.count { it.itemId == c.id && it.response == "true" && (it.rank == null || it.rank == 1) }
                MapCandidateDto(c.name, c.affiliation, partyOf(c.affiliation), if (ok) pct(yes, voters) else null, if (r.live) yes else null)
            }.sortedWith(compareByDescending<MapCandidateDto> { it.pct ?: -1 }.thenBy { it.name })

            // The measure among this district ballot's voters.
            val split = m?.takeIf { it.visibility.mode != "HIDDEN" && !it.suppressed }?.let { mr ->
                val users = r.answers.map { it.userId }.toSet()
                val group = mr.answers.filter { it.userId in users }
                val n2 = group.map { it.userId }.distinct().size
                if (n2 == 0 || (!mr.live && n2 < mr.visibility.groupMinimum)) null
                else group.count { it.response == "true" }.let { y -> MapMeasureSplitDto(pct(y, group.size), pct(group.size - y, group.size)) }
            }
            MapDistrictDto(n, e.id, office?.name, ok, list, split)
        }
        return StateMapDto(st.initial, st.name, live, stateMeasures.map { MapMeasureDto(it.id, it.title) }, rows)
    }
}
