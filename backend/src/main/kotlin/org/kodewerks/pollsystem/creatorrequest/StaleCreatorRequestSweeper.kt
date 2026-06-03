package com.pollsystem.creatorrequest

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class StaleCreatorRequestSweeper(private val service: CreatorRequestService) {

    @Scheduled(fixedDelayString = "\${app.creator-requests.sweep-interval-ms:1800000}")
    fun sweep() {
        val threshold = Instant.now().minus(48, ChronoUnit.HOURS)
        service.unassignStale(threshold)
    }
}
