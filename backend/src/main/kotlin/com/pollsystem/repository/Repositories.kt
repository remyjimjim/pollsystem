package com.pollsystem.repository

import com.pollsystem.model.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : JpaRepository<User, Long> {
    fun findByEmail(email: String): User?
    fun findByPhone(phone: String): User?
    fun existsByEmail(email: String): Boolean
    fun existsByPhone(phone: String): Boolean
    fun findByAccess(access: AccessLevel): List<User>
    fun findByAccessIn(access: List<AccessLevel>): List<User>
    fun findByEmailContainingIgnoreCaseOrderByEmail(fragment: String): List<User>
    fun findByEmailStartingWithIgnoreCaseOrderByEmail(prefix: String): List<User>
    fun findByStripeCustomerId(stripeCustomerId: String): User?
    fun findByStripeSubscriptionId(stripeSubscriptionId: String): User?
}

@Repository
interface MagicLinkTokenRepository : JpaRepository<MagicLinkToken, Long> {
    fun findByTokenHash(tokenHash: String): MagicLinkToken?
}

@Repository
interface StripeEventRepository : JpaRepository<StripeEvent, Long> {
    fun existsByStripeEventId(stripeEventId: String): Boolean
}

@Repository
interface RoleAssignmentRepository : JpaRepository<RoleAssignment, Long> {
    fun findByUserAndRole(user: User, role: AccessLevel): List<RoleAssignment>
    fun findByRoleAndZipcodeAndEnabled(role: AccessLevel, zipcode: String, enabled: Boolean): List<RoleAssignment>
    fun findByUserIdAndRole(userId: Long, role: AccessLevel): List<RoleAssignment>
    fun findByCreatorRequestId(creatorRequestId: Long): List<RoleAssignment>
    fun findByCreatorRequestIdIn(creatorRequestIds: List<Long>): List<RoleAssignment>
    fun findByAdminRequestId(adminRequestId: Long): List<RoleAssignment>
    fun findByAdminRequestIdIn(adminRequestIds: List<Long>): List<RoleAssignment>
    fun findByUserIdInAndRole(userIds: List<Long>, role: AccessLevel): List<RoleAssignment>

    @Query("""
        SELECT ra FROM RoleAssignment ra 
        WHERE ra.role = :role 
        AND ra.zipcode IN :zipcodes 
        AND ra.enabled = true
    """)
    fun findEnabledByRoleAndZipcodes(
        @Param("role") role: AccessLevel,
        @Param("zipcodes") zipcodes: List<String>
    ): List<RoleAssignment>
}

@Repository
interface IpRuleRepository : JpaRepository<IpRule, Long> {
    fun findByTypeAndEnabled(type: IpRuleType, enabled: Boolean): List<IpRule>
}

@Repository
interface AdminRequestRepository : JpaRepository<AdminRequest, Long> {
    fun findByStatus(status: RequestStatus): List<AdminRequest>
    fun findByUserId(userId: Long): List<AdminRequest>
}

@Repository
interface CreatorRequestRepository : JpaRepository<CreatorRequest, Long> {
    fun findByStatus(status: RequestStatus): List<CreatorRequest>
    fun findByAssignedAdminAndStatus(admin: User, status: RequestStatus): List<CreatorRequest>
    fun findByUserId(userId: Long): List<CreatorRequest>
    fun countByAssignedAdminAndStatus(admin: User, status: RequestStatus): Long
    fun countByStatus(status: RequestStatus): Long

    /** Recent decisions made by `admin`, newest first. Used by the admin audit log. */
    @Query("""
        SELECT cr FROM CreatorRequest cr
        WHERE cr.processedBy = :admin
        AND cr.status <> 'PENDING'
        ORDER BY cr.processedAt DESC
    """)
    fun findRecentDecisionsBy(
        @Param("admin") admin: User,
        pageable: org.springframework.data.domain.Pageable
    ): List<CreatorRequest>

    /** Per-admin pending counts, used by the Super admin-workload view. */
    @Query("""
        SELECT cr.assignedAdmin.id AS adminId, COUNT(cr.id) AS pending
        FROM CreatorRequest cr
        WHERE cr.status = 'PENDING' AND cr.assignedAdmin IS NOT NULL
        GROUP BY cr.assignedAdmin.id
    """)
    fun pendingCountsByAdmin(): List<Array<Any>>

    @Query("""
        SELECT cr FROM CreatorRequest cr
        WHERE cr.status = 'PENDING'
        AND cr.submittedAt < :threshold
    """)
    fun findStaleRequests(@Param("threshold") threshold: java.time.Instant): List<CreatorRequest>
}

@Repository
interface StateRepository : JpaRepository<State, Long> {
    fun findByInitial(initial: String): State?
}

@Repository
interface CountyRepository : JpaRepository<County, Long> {
    fun findByStateId(stateId: Long): List<County>
    fun findByStateIdIn(stateIds: List<Long>): List<County>
    fun findByNameStartingWithIgnoreCaseOrderByName(prefix: String): List<County>
}

@Repository
interface CountyZipsRepository : JpaRepository<CountyZips, Long> {
    fun findByCountyId(countyId: Long): List<CountyZips>
    fun findByZipcode(zipcode: String): List<CountyZips>
    fun findByZipcodeIn(zipcodes: List<String>): List<CountyZips>
    fun findByCountyIdIn(countyIds: List<Long>): List<CountyZips>
    fun findByZipcodeStartingWithOrderByZipcode(prefix: String): List<CountyZips>
}

@Repository
interface PollTypeRepository : JpaRepository<PollType, Long>

@Repository
interface OfficeRepository : JpaRepository<Office, Long> {
    fun findByNameIgnoreCase(name: String): Office?
}

@Repository
interface ElectionRepository : JpaRepository<Election, Long> {
    fun findByCreatorId(creatorId: Long): List<Election>
    fun findByStatus(status: PollStatus): List<Election>
    fun findByStatusAndZipcode(status: PollStatus, zipcode: String): List<Election>

    @Query("""
        SELECT e FROM Election e
        WHERE e.status = 'PUBLISHED'
        AND (e.closeDate IS NULL OR e.closeDate > :now)
    """)
    fun findActive(@Param("now") now: java.time.Instant): List<Election>

    @Query("""
        SELECT e FROM Election e 
        WHERE e.status = 'PUBLISHED' 
        AND e.closeDate IS NOT NULL 
        AND e.closeDate <= :now
    """)
    fun findExpiredElections(@Param("now") now: java.time.Instant): List<Election>

    @Query("""
        SELECT e FROM Election e 
        WHERE e.status = 'PUBLISHED' 
        AND e.zipcode IN :zipcodes
    """)
    fun findPublishedByZipcodes(@Param("zipcodes") zipcodes: List<String>): List<Election>
}

@Repository
interface CandidateRepository : JpaRepository<Candidate, Long> {
    fun findByElectionId(electionId: Long): List<Candidate>
    fun findByNameContainingIgnoreCase(name: String): List<Candidate>
}

@Repository
interface CandidateResponseRepository : JpaRepository<CandidateResponse, Long> {
    fun findByUserIdAndCandidateId(userId: Long, candidateId: Long): CandidateResponse?
    fun findByCandidateId(candidateId: Long): List<CandidateResponse>
    fun existsByUserIdAndCandidateId(userId: Long, candidateId: Long): Boolean

    @Query("""
        SELECT cr FROM CandidateResponse cr
        WHERE cr.candidate.election.id = :electionId
        AND cr.user.id = :userId
    """)
    fun findByElectionIdAndUserId(
        @Param("electionId") electionId: Long,
        @Param("userId") userId: Long
    ): List<CandidateResponse>

    @Query("""
        SELECT cr FROM CandidateResponse cr
        WHERE cr.candidate.election.id = :electionId
    """)
    fun findByElectionId(@Param("electionId") electionId: Long): List<CandidateResponse>
}

@Repository
interface BallotMeasureRepository : JpaRepository<BallotMeasure, Long> {
    fun findByElectionId(electionId: Long): List<BallotMeasure>
    fun findByTitleContainingIgnoreCase(title: String): List<BallotMeasure>
    fun findByStatus(status: PollStatus): List<BallotMeasure>

    @Query("""
        SELECT bm FROM BallotMeasure bm
        WHERE bm.status = 'PUBLISHED'
        AND (bm.closeDate IS NULL OR bm.closeDate > :now)
    """)
    fun findActive(@Param("now") now: java.time.Instant): List<BallotMeasure>

    @Query("""
        SELECT bm FROM BallotMeasure bm
        WHERE bm.status = 'PUBLISHED'
        AND bm.closeDate IS NOT NULL
        AND bm.closeDate <= :now
    """)
    fun findExpiredBallotMeasures(@Param("now") now: java.time.Instant): List<BallotMeasure>
}

@Repository
interface BallotResponseRepository : JpaRepository<BallotResponse, Long> {
    fun findByUserIdAndMeasureId(userId: Long, measureId: Long): BallotResponse?
    fun findByMeasureId(measureId: Long): List<BallotResponse>
    fun existsByUserIdAndMeasureId(userId: Long, measureId: Long): Boolean
}

@Repository
interface QuestionnaireRepository : JpaRepository<Questionnaire, Long> {
    fun findByCreatorId(creatorId: Long): List<Questionnaire>
    fun findByStatus(status: PollStatus): List<Questionnaire>

    @Query("""
        SELECT q FROM Questionnaire q
        WHERE q.status = 'PUBLISHED'
        AND (q.closeDate IS NULL OR q.closeDate > :now)
    """)
    fun findActive(@Param("now") now: java.time.Instant): List<Questionnaire>

    @Query("""
        SELECT q FROM Questionnaire q
        WHERE q.status = 'PUBLISHED'
        AND q.closeDate IS NOT NULL
        AND q.closeDate <= :now
    """)
    fun findExpiredQuestionnaires(@Param("now") now: java.time.Instant): List<Questionnaire>
}

@Repository
interface QuestionnaireDomainRepository : JpaRepository<QuestionnaireDomain, Long> {
    fun findByQuestionnaireId(questionnaireId: Long): List<QuestionnaireDomain>
    fun findByZipcode(zipcode: String): List<QuestionnaireDomain>
}

@Repository
interface QuestionRepository : JpaRepository<Question, Long> {
    fun findByQuestionnaireId(questionnaireId: Long): List<Question>
}

@Repository
interface UserMessageRepository : JpaRepository<UserMessage, Long> {
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<UserMessage>
    fun findByUserIdInOrderByCreatedAtDesc(userIds: List<Long>): List<UserMessage>

    /** User ids whose message history contains the given substring (case-insensitive). */
    @Query("""
        SELECT DISTINCT m.userId FROM UserMessage m
        WHERE LOWER(m.body) LIKE LOWER(CONCAT('%', :needle, '%'))
    """)
    fun findUserIdsWithBodyContaining(@Param("needle") needle: String): List<Long>
}

@Repository
interface QuestionResponseRepository : JpaRepository<QuestionResponse, Long> {
    fun findByUserIdAndQuestionId(userId: Long, questionId: Long): QuestionResponse?
    fun findByQuestionId(questionId: Long): List<QuestionResponse>
    fun existsByUserIdAndQuestionId(userId: Long, questionId: Long): Boolean

    @Query("""
        SELECT qr FROM QuestionResponse qr
        WHERE qr.question.questionnaire.id = :questionnaireId
        AND qr.user.id = :userId
    """)
    fun findByQuestionnaireIdAndUserId(
        @Param("questionnaireId") questionnaireId: Long,
        @Param("userId") userId: Long
    ): List<QuestionResponse>

    @Query("""
        SELECT qr FROM QuestionResponse qr
        WHERE qr.question.questionnaire.id = :questionnaireId
    """)
    fun findByQuestionnaireId(@Param("questionnaireId") questionnaireId: Long): List<QuestionResponse>
}
