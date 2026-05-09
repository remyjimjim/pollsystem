# Testing on Fly.io

Three flavours of testing, from cheapest to most expensive:

1. **Post-deploy smoke tests** — fast HTTP checks against a freshly deployed app.
2. **Integration tests against a preview environment** — full request flows on
   real Fly + Neon + Upstash + Stripe (test mode), driven from CI.
3. **Load tests** — validate the lowest-cost staging config holds the
   5,000-concurrent / 200,000-user targets in `docs/COSTS.md`.

Local tests in `docs/TESTING-LOCAL.md` are a prerequisite for any of the
below — those run on every push and gate merges before anything reaches Fly.

---

## 1. Post-deploy smoke tests

Run these on every deploy. They should take under 30 seconds and surface
"deployed but broken" cases that local tests can't (DB connectivity, secrets
present, webhook signature path reachable).

### Manually

```bash
# 1. Health endpoint responds 200.
curl -fsS https://api.poll.example.com/actuator/health
# {"status":"UP"}

# 2. A representative public endpoint responds.
curl -fsS https://api.poll.example.com/api/poll-types | jq '.[0]'

# 3. A protected endpoint returns 401 (not 500).
curl -s -o /dev/null -w '%{http_code}\n' https://api.poll.example.com/api/admin/creators
# 401

# 4. Frontend SPA serves index.html for unknown routes.
curl -fsS https://poll.example.com/some/unknown/path | grep -q '<div id="app">'
```

### Scripted

`scripts/smoke.sh` (not yet checked in — proposed shape):

```bash
#!/usr/bin/env bash
set -euo pipefail
HOST="${1:-https://api.poll.example.com}"

curl -fsS "$HOST/actuator/health" | jq -e '.status == "UP"' >/dev/null
curl -fsS "$HOST/api/poll-types" | jq -e 'length > 0' >/dev/null
test "$(curl -s -o /dev/null -w '%{http_code}' "$HOST/api/admin/creators")" = "401"

echo "smoke: ok ($HOST)"
```

Wire this into the CI deploy job (see "GitHub Actions wiring" below) so a
failed smoke test fails the deploy.

---

## 2. Integration tests against a preview environment

The cleanest way to run end-to-end tests on Fly without touching production:

- **Separate Fly app** for staging: `civicchain-backend-staging` and `civicchain-frontend-staging`.
- **Separate Neon branch** so migrations and data don't leak between staging and prod. Neon branches are zero-cost and fork from a parent in seconds — see https://neon.tech/docs/introduction/branching.
- **Stripe test mode** keys throughout staging.
- **Separate Upstash database** for staging (or namespace keys with a prefix).

Each push to `main` deploys to staging; promotion to prod is a manual workflow
trigger or a tag.

### What to test against staging

- **Auth flow**: request magic link → confirm email arrives in SendGrid (or via Mailpit if you mirror it) → click link → session established.
- **Stripe subscription**: trigger `stripe trigger checkout.session.completed` against the staging webhook URL → assert backend marks the user `paid_until` correctly.
- **Poll lifecycle**: create poll as creator → respond as anonymous via token → fetch results.
- **Migration sanity**: `flyctl ssh console -a civicchain-backend-staging` and `psql` to verify the latest Flyway migration ran.

These are the same flows local integration tests cover — running them on real
infrastructure catches the integration seams (TLS, IP allow-listing on Neon,
Stripe webhook signature mismatch, SendGrid sender-domain issues) that a
Testcontainer can't reproduce.

### Skeleton: a CI job that deploys + tests + promotes

```yaml
# .github/workflows/deploy-staging.yml
name: Deploy staging
on:
  push:
    branches: [main]

jobs:
  deploy-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: superfly/flyctl-actions/setup-flyctl@master

      - name: Deploy backend
        run: flyctl deploy -a civicchain-backend-staging --remote-only
        working-directory: backend
        env:
          FLY_API_TOKEN: ${{ secrets.FLY_API_TOKEN }}

      - name: Deploy frontend
        run: flyctl deploy -a civicchain-frontend-staging --remote-only
        working-directory: frontend
        env:
          FLY_API_TOKEN: ${{ secrets.FLY_API_TOKEN }}

      - name: Smoke
        run: ./scripts/smoke.sh https://api-staging.poll.example.com

      - name: Integration suite (Stripe, magic-link, poll lifecycle)
        run: ./scripts/integration.sh
        env:
          STAGING_HOST: https://api-staging.poll.example.com
          STRIPE_TEST_KEY: ${{ secrets.STRIPE_TEST_KEY }}
          MAGIC_LINK_TEST_INBOX: ${{ secrets.STAGING_TEST_INBOX_TOKEN }}
```

---

## 3. Load tests

The point of load testing is to **falsify** the claim in `docs/COSTS.md` that
the lowest-cost path holds 5,000 concurrent users on 3–5× shared-cpu-1x. Run
this before declaring staging production-ready.

### Tool: k6

`k6` is the easiest to script and produces clean reports.

`scripts/load/poll-mix.js`:

```js
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    // Ramp from 0 to 5000 VUs over 5 min, hold for 10 min, ramp down.
    sustained: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '5m',  target: 5000 },
        { duration: '10m', target: 5000 },
        { duration: '2m',  target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed:   ['rate<0.01'],   // <1% errors
    http_req_duration: ['p(95)<500'],   // 95th-percentile under 500 ms
  },
};

const HOST = __ENV.HOST;

export default function () {
  // 80% of traffic: list polls + read one.
  const list = http.get(`${HOST}/api/polls?status=PUBLISHED`);
  check(list, { 'list ok': (r) => r.status === 200 });

  if (Math.random() < 0.2) {
    // 20%: submit a response (simulates load on writes).
    http.post(`${HOST}/api/polls/123/responses`, JSON.stringify({ answer: 'A' }), {
      headers: { 'Content-Type': 'application/json' },
    });
  }

  sleep(Math.random() * 2);
}
```

Run it:

```bash
k6 run -e HOST=https://api-staging.poll.example.com scripts/load/poll-mix.js
```

### What "passing" looks like

| Metric | Target | Why |
|---|---|---|
| `http_req_failed` | < 1 % | Errors should not climb under load. Above this, autoscale isn't keeping up. |
| `http_req_duration` p(95) | < 500 ms | User-perceived responsiveness threshold. |
| Backend autoscale | settled at 3–5 machines during the hold phase | Confirms the `min_machines_running` and `auto_start_machines` settings work. |
| Neon DB CPU | < 70 % during the hold phase | Otherwise upgrade Neon plan or cache reads. |

If any of these fail, the cost doc's "5,000 concurrent on 3–5× shared-cpu-1x"
claim is false in your concrete config — investigate before promoting to prod.

### Don't load-test prod

Run load against staging only. Stripe test mode is fine; Stripe live mode is
not (you'll trigger fraud heuristics). Use a dedicated Neon branch so the
write traffic doesn't pollute prod data.

---

## GitHub Actions wiring

Required secrets in the repo settings:

| Secret | Source |
|---|---|
| `FLY_API_TOKEN` | `flyctl tokens create deploy` |
| `STRIPE_TEST_KEY` | Stripe dashboard, test mode |
| `STAGING_TEST_INBOX_TOKEN` | An IMAP/API token for a dedicated test inbox the integration suite can poll for magic-link emails |

Suggested workflow file layout:

| File | Purpose | Trigger |
|---|---|---|
| `.github/workflows/ci.yml` | Local-style tests: `gradle test`, `npm test`, `npm run build` (already exists). | push, PR |
| `.github/workflows/deploy-staging.yml` | Deploys to staging Fly apps, runs smoke + integration. | push to main, manual |
| `.github/workflows/load.yml` | Manual k6 run against staging. | manual (workflow_dispatch) |
| `.github/workflows/deploy-prod.yml` | Promotes the last green staging build to prod after manual approval. | tag, manual |

Only `ci.yml` exists today; the other three are scaffolding to add when you
move past local development.

---

## Quick reference

| Question | Answer |
|---|---|
| "Did my deploy actually work?" | `scripts/smoke.sh` — < 30 s. |
| "Does the magic-link / Stripe / poll-response loop work end-to-end?" | Integration suite against staging — minutes. |
| "Will it hold 5,000 concurrent at the lowest-cost config?" | k6 load test against staging — ~20 min. |
| "Is prod healthy right now?" | `flyctl status -a civicchain-backend` + Fly metrics dashboard. |
