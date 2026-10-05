# Swagger UI (local dev only)

Browse every backend endpoint and call it with your own arguments from a web
page. Swagger UI is set up for **local development only**: staging and
production never serve it.

## Open it

Start the local stack (see `docs/DEPLOYING-LOCAL.md`), for example:

```bash
./scripts/BuildAndDeploy.bash local-docker
```

Then open **<http://localhost:8080/swagger-ui.html>** in your host browser.

It lists every endpoint (90 as of October 2026), grouped by controller, each
with its parameters, defaults and request/response shapes. The raw OpenAPI
description is at <http://localhost:8080/v3/api-docs>.

## Call endpoints from the page

1. **Get a token.** Under **dev-controller**, run **`POST /api/dev/token`** with
   `email = admin@local.test` (or any local user's email) and copy the `token`
   from the response. `POST /api/dev/seed-user` also returns a token, so you can
   create a user and get its token in one step.
2. **Click Authorize** at the top right, paste the token, and click
   **Authorize**. It stays saved when you reload the page.
3. **Pick any endpoint**, fill in the fields (they open ready to edit), and
   click **Execute**. You'll see the response, the status code, and the
   equivalent `curl` command.

The token decides what you're allowed to do. An ADMIN token can call
`/api/admin/...`, a USER token can't, and some endpoints (search, geography,
poll types, health) work with no token at all. For example, with an admin token
`GET /api/admin/creators` returns 200; without one it returns 401.

To test as a different role, get a token for a user with that access level
(next section), then click **Authorize → Logout** and paste the new token.

## Seeding test users and data

The dev endpoints exist only under the `local` profile. Each takes an
`emailPrefix` (default `zzz`) so test data can be wiped in one call.

| Endpoint | What it does |
|---|---|
| `POST /api/dev/seed-user?access=USER&zipcode=80202` | Creates a paid member and returns `id`, `email`, `token`. `access`: `VIEWER`, `USER`, `CREATOR`, `ADMIN` or `SUPER`. A `CREATOR` also gets nationwide creator access. |
| `POST /api/dev/token?email=…` | A JWT for an existing user (404 if no such email). |
| `POST /api/dev/seed-questionnaire` | A published questionnaire (and its creator). |
| `POST /api/dev/seed-ballot-measure?zipcode=80202` | A published ballot measure (and its creator and election). |
| `POST /api/dev/seed-ballot-responses?measureId=…&count=3` | Responses from freshly seeded users. |
| `POST /api/dev/reset-test-users?emailPrefix=zzz` | Deletes every user with that prefix and everything they created. |

The same calls from a terminal:

```bash
# Host terminal (the backend's port is published on localhost):
curl -X POST "http://localhost:8080/api/dev/seed-user?access=USER&zipcode=90001"
TOKEN=$(curl -s -X POST "http://localhost:8080/api/dev/token?email=admin@local.test" | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])')
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/admin/creators

# Dev Container terminal: use host.docker.internal instead of localhost.
curl -X POST "http://host.docker.internal:8080/api/dev/seed-user?access=USER"
```

To sign in to the web app as a seeded user instead, enter its email at
<http://localhost:3000/login> and open the sign-in link in Mailpit
(<http://localhost:8025>). The e2e tests seed the same way, through
`seedUser()` in `frontend/e2e/seed.ts`.

## Local only: how it's enforced

| Piece | Where | Effect |
|---|---|---|
| springdoc off by default | `backend/src/main/resources/application.yml` | Staging and prod don't serve Swagger or `/v3/api-docs`. |
| springdoc off for tests | `backend/src/test/resources/application.yml` | This file *replaces* the main one on the test classpath, so it repeats the "off" setting. |
| springdoc on | `backend/src/main/resources/application-local.yml` | The only place it's enabled. |
| Authorize button | `dev/OpenApiConfig.kt` (`@Profile("local")`) | Declares the `Authorization: Bearer <JWT>` scheme. |
| Dev endpoints | `dev/DevController.kt` (`@Profile("local")`) | Don't exist outside local. |
| Security | `security/SecurityConfig.kt` | Permits `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`, `/api/dev/**`. Outside local they return 404. |
| Guard test | `dev/LocalOnlyToolsTest.kt` | Fails if Swagger, the API description or the dev endpoints answer under a non-local profile. |

Version: `springdoc-openapi-starter-webmvc-ui` **2.6.0**, the line built
against Spring Boot 3.3 (springdoc 2.7+ targets Boot 3.4+). Upgrade it together
with Spring Boot.

## Troubleshooting

- **404 on `/swagger-ui.html` locally:** the backend isn't running with the
  `local` profile. It's the default profile, so check that nothing sets
  `SPRING_PROFILES_ACTIVE` to something else.
- **401 on a call:** no token, or an expired one (tokens last 90 days). Get a
  fresh one from `POST /api/dev/token`.
- **403 on a call:** the token's user lacks the role, e.g. a USER calling
  `/api/admin/...`.
- **The page doesn't load from inside the Dev Container:** open it in your host
  browser at `localhost:8080`. Inside the container, `localhost` isn't the
  backend; use `host.docker.internal:8080` for `curl`.
