package org.kodewerks.pollsystem.model

enum class AccessLevel {
    VIEWER,
    USER,
    CREATOR,
    ADMIN,
    SUPER
}

enum class RequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}

enum class PollStatus {
    DRAFT,
    PUBLISHED,
    CLOSED,
    ARCHIVED
}

/**
 * Granularity of a RoleAssignment grant. ZIP is the historical default (one row
 * per zipcode); COUNTY/STATE/NATIONAL let a single row stand for a whole county,
 * state, or the nation, so a coarse creator purview isn't fanned out to
 * thousands of per-zip rows.
 */
enum class ScopeLevel {
    ZIP,
    COUNTY,
    STATE,
    NATIONAL
}
