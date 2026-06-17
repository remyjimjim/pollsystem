# pollsystem

A civic polling platform — questionnaires, elections, and ballot measures
scoped to geographic areas by ZIP code.

- **Backend** — Spring Boot 3 / Kotlin / Java 17, PostgreSQL with Flyway
  migrations, magic-link (passwordless) authentication, Stripe billing.
- **Frontend** — Vue 3 / Vite / TypeScript, Pinia, Vue Router, Tailwind v4,
  internationalized with `vue-i18n` (9 locales).

## Documentation

| Document | What it covers |
|---|---|
| [docs/DEVLOG.md](docs/DEVLOG.md) | **Development log** — every change, paired with the request that prompted it and the commit it shipped in. Start here for project history. |
| [docs/BUILDING.md](docs/BUILDING.md) | Building the backend and frontend; prerequisites and the Gradle wrapper. |
| [docs/DEPLOYING-LOCAL.md](docs/DEPLOYING-LOCAL.md) | Running the full stack on a developer machine. |
| [docs/DEPLOYING-FLY.md](docs/DEPLOYING-FLY.md) | Deploying to Fly.io. |
| [docs/TESTING-LOCAL.md](docs/TESTING-LOCAL.md) | Running the test suites locally. |
| [docs/TESTING-FLY.md](docs/TESTING-FLY.md) | Testing against a Fly.io deployment. |
| [docs/COSTS.md](docs/COSTS.md) | Hosting cost forecasts for staging and production. |

## Contributing

When you make a change, add an entry to **[docs/DEVLOG.md](docs/DEVLOG.md)**
in the same commit — see the "Entry format" section at the top of that file
for the template and conventions.
