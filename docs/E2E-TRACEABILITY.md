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

| Use case | Actor | Backend test | E2E | Prio | Notes |
|---|---|---|---|---|---|
| Sign In via Magic Link | Viewer | ✅ `AuthControllerTest` | ✅ `register-colorado-users` | — | Only use case with e2e today |
| Send Magic Link | (system) | ✅ `AuthControllerTest` | ✅ via registration | — | Exercised through Mailpit |
| Complete Poll via Link | Viewer | ➖ `*ResponsesTest` (submit logic) | ❌ | **P1** | Core unauth path; `/poll/<token>` UX unverified |
| View Poll Results | Viewer | ✅ `*ResultsTest` | ❌ | **P2** | k-anonymity threshold (10) surfacing in UI |
| Search Polls | Registered | ✅ `PollSearchControllerTest` | ❌ | **P1** | Backend covered 2026-07-21; e2e still a gap |
| Complete Poll | Registered | ✅ `*ResponsesTest` | ❌ | **P1** | Authenticated completion via UI |
| Create Creator Request | Registered | ✅ `CreatorRequestServiceTest` | ❌ | P3 | Backend well-covered |
| Submit Creator Request | Registered | ✅ `CreatorRequestServiceTest` | ❌ | P3 | |
| Stripe Checkout | Viewer/Reg | ➖ session-create | ❌ | **P1** | Revenue entry; redirect flow unverified |
| Webhook: checkout.completed | Stripe | ✅ `StripeWebhookControllerTest` | ➖ | P2 | e2e needs Stripe CLI / test event |
| Provision Paid User | (system) | ✅ `StripeWebhookControllerTest` | ➖ | P2 | Tail of the checkout chain |
| Create Poll | Creator | ✅ `*ServiceTest` | ❌ | **P1** | UI creation wizard unverified |
| Select Poll Type and Domain | Creator | ✅ `PollDraftValidationTest` | ❌ | **P1** | Part of Create Poll flow |
| Questionnaire | Creator | ✅ `Questionnaire*Test` | ❌ | **P1** | Create + respond via UI |
| Election | Creator | ✅ `Election*Test` | ❌ | **P1** | Create + respond via UI |
| Referendum / Ballot Measure | Creator | ✅ `BallotMeasure*Test` | ❌ | **P1** | Create + respond via UI |
| Generate Respondent Link | Creator | — **not built** | — | — | UML-only; see "Unbuilt use case" below |
| Submit Admin Request | Creator | ✅ `AdminRequestServiceTest` | ❌ | P3 | |
| Approve Creator | Admin | ✅ `AdminCreatorRequestsTest` | ❌ | P2 | Queue → approve → access granted |
| Manage Creators | Admin | ➖ `SuperUsersControllerTest` | ❌ | P3 | |
| Manage Polls | Admin | ✅ `AdminPollsControllerTest` | ❌ | P2 | Block/note within purview |
| Manage IP allow/deny lists | Super | ✅ `SuperIpRuleControllerTest` | ❌ | P3 | |
| Create/Edit Poll Types (+JSON) | Super | ✅ `SuperPollTypeControllerTest` | ❌ | P2 | JSON-template editor is fiddly UI |
| Approve Admin Request | Super | ✅ `AdminRequestServiceTest` | ❌ | P3 | |
| Manage Admins | Super | ✅ `SuperUsersControllerTest` | ❌ | P3 | |
| Webhook: subscription.updated | Stripe | ✅ `StripeWebhookControllerTest` | ➖ | P3 | `paid_until` refresh |
| Webhook: subscription.deleted | Stripe | ✅ `StripeWebhookControllerTest` | ➖ | P3 | Revoke paid access |

## Recommended e2e build order (risk-ranked)

**P1 — build first** (critical path × untested wiring):

1. **Search → Complete** (registered) — search, open a result, submit. Backend
   search is now covered (`PollSearchControllerTest`); the browser round-trip is
   not.
2. **Stripe Checkout → provision → magic-link login** — the revenue path,
   end-to-end (Stripe test mode + CLI-forwarded webhook), landing on a
   provisioned, signed-in paid user.
4. **Creator creates each poll type via the UI** — questionnaire, election,
   ballot measure through the creation wizard (backend logic is covered; the
   wizard wiring is not).

**P2 — next:**

5. View Poll Results incl. k-anonymity (fewer than 10 responses hides
   aggregates).
6. Admin approves a creator request end-to-end (queue → approve → creator gains
   access on next login).
7. Super creates/edits a poll type with its JSON template.

**P3 — low marginal value** (already strongly backend-covered; add e2e only if a
regression appears): the remaining request/approval and super-management flows,
and the subscription webhooks.

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

- **Run e2e in CI.** Today `ci.yml` runs backend `gradle test` + frontend
  type-check/test/build, but **not Playwright** — which is why e2e has drifted to
  1-of-N coverage. Even a P1-only job (spin up db + backend + Mailpit, run the
  critical specs) stops the drift.
- **Reuse exists.** New specs inherit the hard parts already solved: magic-link
  extraction via Mailpit (`e2e/mailpit.ts`), per-role isolated browser contexts,
  and `zzz`-prefixed teardown (`playwright/global-teardown.ts`).
