package com.pollsystem.model

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
