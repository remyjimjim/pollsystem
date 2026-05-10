# Building the App

This repo has two build targets: the **Spring Boot / Kotlin backend** and the
**Vue 3 / Vite frontend**. They are independent — you can build either without
the other.

## Prerequisites

| Tool | Version | Used for |
|---|---|---|
| JDK | **17** (Temurin/Adoptium recommended) | Backend compile + test |
| Node.js | **20.x** | Frontend build + test |
| npm | bundled with Node 20 | Frontend dependency install |
| Docker | latest | Local Postgres, Testcontainers integration tests |

The backend uses the **Gradle wrapper** (`gradlew`), which is committed to the
repo together with `gradle/wrapper/gradle-wrapper.jar` and pins Gradle 8.10.2.
You don't need a system Gradle install — the wrapper downloads its own
distribution into `~/.gradle/` on first run. Just make sure you have a JDK 17
on `JAVA_HOME` (a system Java 25 will run the wrapper too, but the build
targets bytecode for 17 and Gradle 8.10.2 only officially supports up to 23).

---

## Backend

```bash
cd backend
./gradlew build
```

What this does:
- Compiles Kotlin sources under `src/main/kotlin` against Spring Boot 3.3.5.
- Runs the test suite (Testcontainers spins up an ephemeral PostgreSQL container — Docker must be running).
- Produces a runnable fat JAR at `build/libs/pollsystem-0.0.1-SNAPSHOT.jar`.

**Skip tests during a build** (e.g. for fast iteration):

```bash
./gradlew build -x test
```

**Run from the JAR**:

```bash
java -jar build/libs/pollsystem-0.0.1-SNAPSHOT.jar
```

The JAR expects a Postgres reachable per `src/main/resources/application.yml`
(default `jdbc:postgresql://localhost:5432/pollsystem`, user `polladmin`,
password `pollpass123`). For other environments, override via env vars or a
custom `application.yml` on the classpath. See `docs/DEPLOYING-LOCAL.md`.

### Database migrations

Migrations live in `backend/src/main/resources/db/migration/` (Flyway, `V1__…` … `V7__…`).
They run automatically on backend startup (`spring.flyway.enabled: true`) against
whichever Postgres the datasource points at. There is no separate migration
command — booting the backend applies any pending migrations.

### What's in the JAR

| Artifact | Path | Notes |
|---|---|---|
| Main class | `com.pollsystem.PollSystemApplicationKt` | Spring Boot entry point |
| Migrations | `db/migration/V*.sql` | bundled into the JAR; applied by Flyway at startup |
| Config | `application.yml` | bundled defaults; override via env vars at runtime |

---

## Frontend

```bash
cd frontend
npm install
npm run build
```

What this does:
- Resolves dependencies into `node_modules/`.
- Type-checks the codebase via `vue-tsc` (the `build` script runs `vue-tsc && vite build`).
- Produces a static SPA bundle at `frontend/dist/` (HTML + hashed JS/CSS).

**Type-check only** (faster than a full build):

```bash
npm run type-check
```

**Preview the production build** (serves `dist/` on port 4173):

```bash
npm run preview
```

### Vite proxy

The dev server (`npm run dev`, port 3000) proxies `/api/*` requests to
`http://localhost:8080`. The production build does not — in deployment, the
frontend either talks to the backend at a known origin (configured at build
time or runtime), or is served behind a reverse proxy that handles the routing.

---

## Putting it together

For a deployable artifact set:

```bash
( cd backend && ./gradlew build )
( cd frontend && npm install && npm run build )
```

Outputs:
- `backend/build/libs/pollsystem-0.0.1-SNAPSHOT.jar` — the runnable backend.
- `frontend/dist/` — static files to serve from any HTTP server (or Fly.io static app).

Both are reproducible from a clean checkout given the prerequisites above. CI
(`.github/workflows/ci.yml`) runs the same steps on every push and PR to `main`.
