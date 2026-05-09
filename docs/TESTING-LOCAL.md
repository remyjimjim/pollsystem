# Testing Locally

Both halves of the stack have their own test suites. They run independently
and share no state.

## Backend

```bash
cd backend
gradle test
```

What runs:
- All unit tests under `src/test/kotlin`.
- All integration tests that extend `AbstractIntegrationTest` (most of them).
- For each integration-test class, **Testcontainers** spins up a fresh
  `postgres:16` container, applies Flyway migrations, and tears it down at the
  end. **Docker must be running** — that's the only environmental requirement.

You do **not** need `docker compose up db` for tests. The Testcontainers
Postgres is independent of the local-dev Postgres and won't interfere with it.

### How tests connect to the DB

`AbstractIntegrationTest` uses `@ServiceConnection` (Spring Boot 3.1+), so
Spring auto-wires the JDBC URL/credentials from the container at runtime.
`backend/src/test/resources/application.yml` only carries non-DB test config
(JWT secret, k-anonymity threshold, disabled cron sweepers).

### Running a single test

```bash
gradle test --tests com.pollsystem.poll.QuestionnaireServiceTest
gradle test --tests 'com.pollsystem.poll.*'
gradle test --tests '*ElectionResultsTest.shouldRedactBelowThreshold'
```

### Reading test reports

After a run:
- HTML: `backend/build/reports/tests/test/index.html`
- JUnit XML (for CI): `backend/build/test-results/test/`

CI uploads the HTML report as a `backend-test-report` artifact when tests fail
(see `.github/workflows/ci.yml`).

### Speeding things up

- **Container reuse**: `AbstractIntegrationTest` uses `.withReuse(true)`. To
  enable reuse you must opt in once on your machine:

  ```bash
  echo 'testcontainers.reuse.enable=true' >> ~/.testcontainers.properties
  ```

  After that, the same Postgres container is reused across `gradle test` invocations. Without it, every run starts a fresh container (~5–10 s overhead).

- **Skip slow tests**: tag-based filtering isn't set up yet; for fast iteration use `--tests` to scope the run.

### Common issues

| Symptom | Likely cause |
|---|---|
| `Could not find a valid Docker environment` | Docker daemon not running. Start Docker Desktop / `systemctl start docker`. |
| Tests pass locally but fail in CI with migration errors | A migration file was edited after being committed. Migrations are immutable once merged — add a new `V<n+1>__…sql` instead. |
| `OutOfMemoryError` during a long run | Bump `org.gradle.jvmargs=-Xmx2g` in `~/.gradle/gradle.properties`. |

---

## Frontend

```bash
cd frontend
npm install   # first time only
npm test
```

What runs:
- All test files matched by Vitest's `include` pattern in `vite.config.ts` (`src/**/*.{test,spec}.ts`).
- Tests run in `happy-dom` (a lightweight JSDOM alternative) — no real browser needed.
- `vitest.setup.ts` wires up Pinia/test plugins.

### Variants

```bash
npm run test:watch         # interactive watch mode
npm run test:coverage      # adds @vitest/coverage-v8, outputs HTML + text
npm run type-check         # vue-tsc only, no tests
```

### Coverage report

After `npm run test:coverage`:
- HTML: `frontend/coverage/index.html`
- Text summary printed to stdout.

### Running a single test

```bash
npx vitest run src/components/PollCard.test.ts
npx vitest run -t "should render closed poll"
```

---

## End-to-end (full stack)

There are no e2e tests configured today. If you want to manually exercise the
full stack:

1. `docker compose up -d db` (Postgres for the backend)
2. `cd backend && gradle bootRun` (waits for migrations + serves on `:8080`)
3. `cd frontend && npm run dev` (serves on `:3000`, proxies `/api` to `:8080`)
4. Open `http://localhost:3000` and click through.

For automated e2e, Playwright is the natural fit and would slot into a third CI
job alongside `backend` and `frontend`. Not in scope yet.

---

## What gets run in CI

`.github/workflows/ci.yml` on every push and PR to `main`:

- **Backend job**: JDK 17 (Temurin), Gradle 8.10.2 via `gradle/actions/setup-gradle@v4`, runs `gradle test`. Uploads `backend-test-report` artifact on failure.
- **Frontend job**: Node 20, runs `npm install`, `npm run type-check`, `npm test`, `npm run build`.

Both jobs run in parallel. A push that breaks either fails the workflow.
