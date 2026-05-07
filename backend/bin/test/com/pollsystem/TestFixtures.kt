package com.pollsystem

import com.pollsystem.model.AccessLevel
import com.pollsystem.model.RoleAssignment
import com.pollsystem.model.User
import com.pollsystem.repository.CountyRepository
import com.pollsystem.repository.RoleAssignmentRepository
import com.pollsystem.repository.StateRepository
import com.pollsystem.repository.UserRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

/**
 * Helpers for building test data. Uses an atomic counter to ensure unique
 * email/phone per call within a test run.
 */
@Component
class TestFixtures @Autowired constructor(
    private val users: UserRepository,
    private val states: StateRepository,
    private val counties: CountyRepository,
    private val roleAssignments: RoleAssignmentRepository,
    private val passwordEncoder: PasswordEncoder
) {
    private val seq = AtomicLong(System.nanoTime())

    fun createUser(
        access: AccessLevel = AccessLevel.USER,
        zipcode: String = "90001",
        passcode: String = "password123",
        emailPrefix: String = "user"
    ): User {
        val n = seq.incrementAndGet()
        return users.save(
            User(
                email = "$emailPrefix-$n@test.local",
                phone = "+1555${n.toString().padStart(7, '0').takeLast(7)}",
                zipcode = zipcode,
                passcode = passwordEncoder.encode(passcode),
                access = access,
                isEnabled = true
            )
        )
    }

    /**
     * Creates an enabled ADMIN RoleAssignment for the given user covering the
     * county that owns the supplied zipcode (CA / Los Angeles for 90001 from V2).
     */
    fun assignAdmin(user: User, stateInitial: String = "CA", countyName: String = "Los Angeles", zipcode: String = "90001"): RoleAssignment {
        val state = states.findByInitial(stateInitial)
            ?: error("State $stateInitial not seeded")
        val county = counties.findByStateId(state.id).firstOrNull { it.name == countyName }
            ?: error("County $countyName not seeded for $stateInitial")
        return roleAssignments.save(
            RoleAssignment(
                user = user,
                role = AccessLevel.ADMIN,
                state = state,
                county = county,
                zipcode = zipcode,
                enabled = true
            )
        )
    }
}
