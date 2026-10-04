package org.kodewerks.pollsystem.geography

import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.BlockScope
import org.kodewerks.pollsystem.model.PollPurview
import org.kodewerks.pollsystem.model.PollTypeBlock
import org.kodewerks.pollsystem.model.RoleAssignment
import org.kodewerks.pollsystem.model.ScopeLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.repository.CountyRepository
import org.kodewerks.pollsystem.repository.CountyZipsRepository
import org.kodewerks.pollsystem.repository.RoleAssignmentRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * One territorial region at a [ScopeLevel], carrying its resolved ancestors so
 * containment needs no further lookups: a COUNTY knows its state, a ZIP its
 * county(ies) and state(s) (a few zips straddle county lines).
 *
 * The levels nest (NATIONAL ⊃ STATE ⊃ COUNTY ⊃ ZIP), so two regions either
 * don't overlap or one contains the other, and their intersection is simply
 * the smaller one. That's what lets "poll ∩ creator ∩ admin" stay a plain
 * list of regions.
 */
data class Region(
    val level: ScopeLevel,
    val stateIds: Set<Long> = emptySet(),
    val countyIds: Set<Long> = emptySet(),
    val zipcode: String? = null
) {
    /** The region's own id at its level (state / county id); null for NATIONAL and ZIP. */
    val id: Long? get() = when (level) {
        ScopeLevel.STATE -> stateIds.single()
        ScopeLevel.COUNTY -> countyIds.single()
        else -> null
    }

    /** Does this region contain all of [other]? */
    fun contains(other: Region): Boolean = when (level) {
        ScopeLevel.NATIONAL -> true
        ScopeLevel.STATE -> other.level != ScopeLevel.NATIONAL && id in other.stateIds
        ScopeLevel.COUNTY -> (other.level == ScopeLevel.COUNTY || other.level == ScopeLevel.ZIP) && id in other.countyIds
        ScopeLevel.ZIP -> other.level == ScopeLevel.ZIP && other.zipcode == zipcode
    }

    /** The shared territory, or null when the two don't overlap. */
    fun intersect(other: Region): Region? = when {
        contains(other) -> other
        other.contains(this) -> this
        else -> null
    }

    fun overlaps(other: Region) = intersect(other) != null

    companion object {
        val NATIONAL = Region(ScopeLevel.NATIONAL)
    }
}

/** Pairwise intersection of two region sets, with regions inside another dropped. */
fun intersectAll(a: Collection<Region>, b: Collection<Region>): List<Region> =
    minimal(a.flatMap { x -> b.mapNotNull { x.intersect(it) } })

/** [regions] without any region already contained in another (and without duplicates). */
fun minimal(regions: Collection<Region>): List<Region> {
    val distinct = regions.distinct()
    return distinct.filter { r -> distinct.none { it != r && it.contains(r) } }
}

/** Builds [Region]s from the app's geo-bearing rows (grants, purviews, blocks, zips). */
@Service
class RegionService(
    private val counties: CountyRepository,
    private val countyZips: CountyZipsRepository,
    private val roleAssignments: RoleAssignmentRepository
) {

    /** An admin's purview as regions (their enabled ADMIN grants); SUPER = nationwide. */
    @Transactional(readOnly = true)
    fun adminRegions(user: User): List<Region> {
        if (user.access >= AccessLevel.SUPER) return listOf(Region.NATIONAL)
        return minimal(
            roleAssignments.findByUserIdAndRole(user.id, AccessLevel.ADMIN).filter { it.enabled }.mapNotNull { ofGrant(it) }
        )
    }

    fun state(stateId: Long) = Region(ScopeLevel.STATE, stateIds = setOf(stateId))

    @Transactional(readOnly = true)
    fun county(countyId: Long): Region? =
        counties.findById(countyId).map { Region(ScopeLevel.COUNTY, setOf(it.state.id), setOf(it.id)) }.orElse(null)

    /** A zip's region, or null for an unknown zip. */
    @Transactional(readOnly = true)
    fun zip(zipcode: String): Region? = zips(listOf(zipcode))[zipcode]

    /** Batch [zip]: one query for many zips (e.g. a state-wide search). */
    @Transactional(readOnly = true)
    fun zips(zipcodes: Collection<String>): Map<String, Region> {
        if (zipcodes.isEmpty()) return emptyMap()
        return countyZips.findByZipcodeIn(zipcodes.distinct()).groupBy { it.zipcode }.mapValues { (z, rows) ->
            Region(
                ScopeLevel.ZIP,
                stateIds = rows.map { it.county.state.id }.toSet(),
                countyIds = rows.map { it.county.id }.toSet(),
                zipcode = z
            )
        }
    }

    /** A grant's territory (by its scope level, not by which columns happen to be filled). */
    @Transactional(readOnly = true)
    fun ofGrant(g: RoleAssignment): Region? = when (g.scopeLevel) {
        ScopeLevel.NATIONAL -> Region.NATIONAL
        ScopeLevel.STATE -> g.state?.let { state(it.id) }
        ScopeLevel.COUNTY -> g.county?.let { Region(ScopeLevel.COUNTY, setOf(it.state.id), setOf(it.id)) }
        ScopeLevel.ZIP -> g.zipcode?.let { zip(it) }
    }

    /** A poll's territory from its purview rows; no rows = nationwide. */
    @Transactional(readOnly = true)
    fun ofPurview(rows: List<PollPurview>): List<Region> {
        if (rows.isEmpty() || rows.any { it.scopeLevel == ScopeLevel.NATIONAL }) return listOf(Region.NATIONAL)
        val byZip = zips(rows.mapNotNull { it.zipcode })
        return rows.mapNotNull { r ->
            when (r.scopeLevel) {
                ScopeLevel.STATE -> r.stateId?.let { state(it) }
                ScopeLevel.COUNTY -> r.countyId?.let { county(it) }
                ScopeLevel.ZIP -> byZip[r.zipcode]
                ScopeLevel.NATIONAL -> Region.NATIONAL
            }
        }
    }

    /** A block's territory (EVERYWHERE = nationwide). */
    @Transactional(readOnly = true)
    fun ofBlock(b: PollTypeBlock): Region? = ofBlock(b.scope, b.zipcode, b.countyId, b.stateId)

    @Transactional(readOnly = true)
    fun ofBlock(scope: BlockScope, zipcode: String?, countyId: Long?, stateId: Long?): Region? = when (scope) {
        BlockScope.EVERYWHERE -> Region.NATIONAL
        BlockScope.STATE -> stateId?.let { state(it) }
        BlockScope.COUNTY -> countyId?.let { county(it) }
        BlockScope.ZIPCODE -> zipcode?.let { zip(it) }
    }
}
