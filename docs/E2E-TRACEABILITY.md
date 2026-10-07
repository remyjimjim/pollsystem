# E2E Traceability Matrix

Maps every use case in [`UML/USE-CASES.plantuml`](UML/USE-CASES.plantuml) to its
current test coverage, and ranks the end-to-end (Playwright) gaps by risk.

**Why coverage ≠ risk here.** The backend has strong Testcontainers integration
coverage (all three poll types' service/responses/results, all three Stripe
webhooks, the creator/admin request + approval chains, super endpoints, purview
scoping). So an "e2e GAP" usually means *the browser + full-stack wiring is
unverified*, not *the logic is untested*. E2E priority therefore tracks
**untested browser/full-stack wiring on critical paths**, not raw coverage.

Legend: ✅ covered · ➖ partial / indirect · ❌ none · **P1** build first →
**P3** low marginal value.

## Matrix

_Refreshed 2026-10-07 against `frontend/e2e/` (6 journey specs) and the backend
suite. Spec names follow `{actor}-{process}`; see `docs/TESTING-E2E.md`._

| Use case | Actor | Backend test | E2E | Prio | Notes |
|---|---|---|---|---|---|
| Sign In via Magic Link | Viewer | ✅ `AuthControllerTest` | ✅ `register-users` + every spec's sign-in | — | Magic link read from Mailpit |
| Send Magic Link | (system) | ✅ `AuthControllerTest` | ✅ via registration / sign-in | — | |
| Complete Poll via Link | Viewer | — **not built** | — | — | Needs the unbuilt respondent link (below); completion today is authenticated |
| View Poll Results | Viewer | ✅ `*ResultsTest` | ✅ `viewer-searches-views-results` | — | Asserts the k-anonymity *withheld* state (≤ 6 seeded responses) |
| Search Polls | Registered | ✅ `PollSearchControllerTest`, `AreaAwareBlocksTest` | ✅ `viewer-searches-views-results`, `user-registers-submits-poll`, `creator-creates-questionnaire` | — | Guest + member search by title |
| Complete Poll | Registered | ✅ `*ResponsesTest` | ➖ `user-registers-submits-poll` | **P1** | Questionnaire only; election + ballot-measure responses via UI unverified |
| Create Creator Request | Registered | ✅ `CreatorRequestServiceTest` | ✅ `user-submits-creator-request` | — | Whole-state scope |
| Submit Creator Request | Registered | ✅ `CreatorRequestServiceTest` | ✅ `user-submits-creator-request` | — | |
| Stripe Checkout | Viewer/Reg | ➖ session-create | ➖ `register-users`, `user-registers-submits-poll` | **P1** | E2E runs the pay-first flow against the local **MockPaymentProvider**; the real Stripe redirect is unverified |
| Webhook: checkout.completed | Stripe | ✅ `StripeWebhookControllerTest` | ➖ | P2 | Mock provider provisions directly; real event needs Stripe CLI |
| Provision Paid User | (system) | ✅ `StripeWebhookControllerTest` | ➖ | P2 | Via the mock provider |
| Create Poll | Creator | ✅ `*ServiceTest`, `CreatorGrantGuardTest` | ➖ `creator-creates-questionnaire` | **P1** | Questionnaire done; election + ballot measure remain |
| Select Poll Type and Domain | Creator | ✅ `PollDraftValidationTest` | ✅ `creator-creates-questionnaire` | — | Type picker + whole-state purview |
| Questionnaire | Creator | ✅ `Questionnaire*Test` | ✅ create: `creator-creates-questionnaire`; respond: `user-registers-submits-poll` | — | |
| Election | Creator | ✅ `Election*Test` | ❌ | **P1** | Create + respond via UI |
| Referendum / Ballot Measure | Creator | ✅ `BallotMeasure*Test` | ➖ results only (`viewer-searches-views-results`) | **P1** | Create + respond via UI unverified (seeded via API) |
| Generate Respondent Link | Creator | — **not built** | — | — | UML-only; see "Unbuilt use case" below |
| Submit Admin Request | Creator | ✅ `AdminRequestServiceTest` | ❌ | P3 | |
| Approve Creator | Admin | ✅ `AdminCreatorRequestsTest` | ❌ | **P2** | Natural continuation of `user-submits-creator-request` |
| Manage Creators | Admin | ✅ `AdminCreatorsControllerTest` | ❌ | **P2** | Raised from P3 (Oct 2026): the stored creator disable now gates poll creation and blocks polls at poll ∩ creator ∩ admin purview |
| Manage Polls | Admin | ✅ `AdminPollsControllerTest`, `AreaAwareBlocksTest` | ❌ | **P2** | Rows stay visible after disabling; zip/county/state disables are area-aware |
| Disable All Poll Submissions (kill switch) | Super | ✅ `PollsKillSwitchTest` | ✅ `super-disables-submissions` | — | Not in the UML yet |
| Manage IP allow/deny lists | Super | ✅ `SuperIpRuleControllerTest` | ❌ | P3 | |
| Create/Edit Poll Types (+JSON) | Super | ✅ `SuperPollTypeControllerTest` | ❌ | P2 | JSON-template editor is fiddly UI |
| Approve Admin Request | Super | ✅ `AdminRequestServiceTest` | ❌ | P3 | |
| Manage Admins | Super | ✅ `SuperUsersControllerTest` | ❌ | P3 | |
| Webhook: subscription.updated | Stripe | ✅ `StripeWebhookControllerTest` | ➖ | P3 | `paid_until` refresh |
| Webhook: subscription.deleted | Stripe | ✅ `StripeWebhookControllerTest` | ➖ | P3 | Revoke paid access |

`seed-users-debug` is an interactive dev tool (bulk seeding with a Close modal),
not a journey spec.

## Recommended e2e build order (risk-ranked)

**Done since the first version of this doc:** search → complete (questionnaire),
view results with k-anonymity, creator request, the super kill switch, and
creator creates a questionnaire.

**P1 — next:**

1. **Creator creates an election and a ballot measure** — same wizard pattern as
   `creator-creates-questionnaire`.
2. **Member answers an election and a ballot measure** via the UI.
3. **Real Stripe Checkout → webhook → magic-link login** in Stripe test mode
   (today's e2e uses the mock provider).

**P2:**

4. **Admin approves a creator** (`admin-approves-creator`), chained after
   `user-submits-creator-request`.
5. **Admin manages creators** (`admin-manages-creators`): disable a creator →
   their poll is disabled only in the admin's area → re-enable one poll from the
   Polls link (creator stays unchecked) → the creator is refused a new poll there.
6. **Admin manages polls** (`admin-manages-polls`): the disabled row stays in
   place; a zip-level disable.
7. Super creates/edits a poll type with its JSON template.

**P3 — low marginal value** (strongly backend-covered): the remaining
request/approval and super-management flows, and the subscription webhooks.

## Unbuilt use case: Generate Respondent Link

`Generate Respondent Link` (the UML's opaque `/poll/<token>` URL that lets a
respondent submit **without authenticating**) **is not implemented.** There is
no generation endpoint, no `/poll/<token>` route, and no token-based submission
path — poll submission falls under `anyRequest().authenticated()`, so today a
respondent must be a logged-in user. The only token in the system is the
magic-link auth token.

So there is nothing to test yet. This is a **UML-vs-implementation discrepancy**
to resolve deliberately: either build the anonymous-token respondent flow, or
amend `USE-CASES.plantuml` to match how completion actually works (authenticated,
by poll id). Until then it carries no priority here.

_(Backend coverage note: `Search Polls` was the other "no test at any level" gap
and now has `PollSearchControllerTest`.)_

## Also recommended

- **Fix the CI e2e job.** `ci.yml` does have an E2E (Playwright) job that
  boots the whole stack, but its run step still names
  `register-colorado-users.spec.ts` and `search-complete.spec.ts` — specs that
  were since renamed — so it finds no tests. Point it at the current
  non-interactive specs (never `seed-users-debug`).
- **Reuse exists.** New specs inherit the hard parts already solved: magic-link
  extraction via Mailpit (`e2e/mailpit.ts`), API seeding (`e2e/seed.ts`, backed by
  the local-only `/api/dev/*` endpoints), per-role isolated browser contexts, and
  `zzz`-prefixed teardown (`playwright/global-teardown.ts`).
