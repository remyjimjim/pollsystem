package org.kodewerks.pollsystem.poll

import org.kodewerks.pollsystem.model.AccessLevel
import org.kodewerks.pollsystem.model.User
import org.kodewerks.pollsystem.security.AppUserDetails
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/**
 * Pure unit test of the participation gate — no Spring context needed.
 */
class ParticipationGuardTest {

    private fun details(
        access: AccessLevel = AccessLevel.USER,
        profileComplete: Boolean = true,
        paidUntil: Instant? = null,
    ) = AppUserDetails(
        User(
            id = 1,
            email = "p@test.local",
            phone = if (profileComplete) "+15551230000" else null,
            zipcode = if (profileComplete) "90001" else null,
            access = access,
            paidUntil = paidUntil,
        )
    )

    @Test
    fun `USER with an active subscription may participate`() {
        assertDoesNotThrow {
            requireParticipation(details(paidUntil = Instant.now().plusSeconds(86_400)))
        }
    }

    @Test
    fun `USER without a subscription is blocked with 402`() {
        val ex = assertThrows<ResponseStatusException> {
            requireParticipation(details(paidUntil = null))
        }
        assertEquals(402, ex.statusCode.value())
    }

    @Test
    fun `USER with an expired subscription is blocked with 402`() {
        val ex = assertThrows<ResponseStatusException> {
            requireParticipation(details(paidUntil = Instant.now().minusSeconds(60)))
        }
        assertEquals(402, ex.statusCode.value())
    }

    @Test
    fun `CREATOR without a subscription is blocked with 402`() {
        val ex = assertThrows<ResponseStatusException> {
            requireParticipation(details(access = AccessLevel.CREATOR, paidUntil = null))
        }
        assertEquals(402, ex.statusCode.value())
    }

    @Test
    fun `SUPER may participate without a subscription`() {
        assertDoesNotThrow {
            requireParticipation(details(access = AccessLevel.SUPER, paidUntil = null))
        }
    }

    @Test
    fun `incomplete profile is blocked with 400 before the paywall`() {
        val ex = assertThrows<ResponseStatusException> {
            requireParticipation(details(profileComplete = false, paidUntil = null))
        }
        assertEquals(400, ex.statusCode.value())
    }
}
