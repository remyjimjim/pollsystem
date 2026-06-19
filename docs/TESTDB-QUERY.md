# Querying the test database

Yes — and the setup is in `backend/src/test/kotlin/org/kodewerks/pollsystem/AbstractIntegrationTest.kt`. Quick facts:

- **Image:** `postgres:16` in a Testcontainers-managed Docker container
- **DB name:** `pollsystem_test` (not `pollsystem`)
- **User / password:** `test` / `test`
- **Host port:** dynamic — Testcontainers maps the container's 5432 to a random free host port
- **Lifecycle:** started once per `./gradlew test` JVM, torn down when the JVM exits via a shutdown hook. Reuse is **intentionally off** (comment in the file explains the rationale)
- **Cleanup model:** Two layers. Schema + Flyway seeds (`V1..V10 + V12`) are committed and persist for the whole run. Data inserted by each `@Test` method is rolled back at method end because `AbstractIntegrationTest` is `@Transactional`

So the window for live inspection is "while the JVM is alive". Four ways to crack it open, easiest first:

**1. Add a pause test (zero config)**

```kotlin
// backend/src/test/kotlin/org/kodewerks/pollsystem/PauseForInspectionTest.kt
package org.kodewerks.pollsystem

import org.junit.jupiter.api.Test

class PauseForInspectionTest : AbstractIntegrationTest() {
    @Test
    fun pause() {
        println("JDBC: ${postgres.jdbcUrl}")
        println("USER/PASS: ${postgres.username} / ${postgres.password}")
        Thread.sleep(10 * 60_000) // 10 min — kill with Ctrl-C when done
    }
}
```

Run just that one:
```bash
cd backend && ./gradlew test --tests '*PauseForInspectionTest*' -i
```
Watch stdout for the `JDBC:` line, then connect:
```bash
psql 'jdbc-url-without-the-jdbc-prefix'
# or, given JDBC: jdbc:postgresql://localhost:54321/pollsystem_test
psql -h localhost -p 54321 -U test -d pollsystem_test
# password: test
```
Note: anything you `INSERT` from psql is **outside** the test transaction — it commits and stays. So you can stage data interactively.

**2. Debugger breakpoint in IntelliJ**

Set a breakpoint mid-test in the test you care about (e.g. `AdminCreatorRequestsTest`), run it in debug mode. At the breakpoint the test's transaction is still open and uncommitted — you'll see the test fixture's data via `READ UNCOMMITTED` only if you connect on the same connection (so this is mostly useful for inspecting **committed** state: Flyway seeds + anything from a `@Commit` test).

**3. `docker ps` during a normal `./gradlew test`**

```bash
docker ps --filter ancestor=postgres:16 --format 'table {{.Names}}\t{{.Ports}}'
```
Window is ~3 min; useful for "is the container actually up" sanity checks more than real querying.

**4. Temporarily flip reuse on**

Edit `AbstractIntegrationTest.kt`:
```kotlin
PostgreSQLContainer("postgres:16")
    .withDatabaseName("pollsystem_test")
    ...
    .withReuse(true)              // ← add
    .also { it.start() }
```
And add `testcontainers.reuse.enable=true` to `~/.testcontainers.properties`. The container persists between runs — query at leisure with psql. The file's comment warns this leads to cross-run pollution (duplicate-key errors, id drift), so this is for **debugging only** — revert before committing.

Heads-up on what you'll see: a per-test method's data won't be visible from outside that method because of the rollback. To inspect a test's effect, either drop a breakpoint inside it (option 2) or put the relevant `INSERT`s straight into your pause test and skip the `@Rollback` with `@Commit` on that method.
