# Development Log

A running record of changes, pairing the request that prompted each one
(in the requester's own words) with a summary of what was done. Newest
entries are added at the top.

Entries below `46469e9` were reconstructed retroactively from `git log`;
their quotes are drawn from the conversation record and may be lightly
elided. The "Pre-record history" block at the end of the file predates the
available conversation record entirely — those entries carry a paraphrased
**Intent** line instead of a quoted **Requested**.

# Entry format

Each entry is a level-2 heading followed by labelled blocks:

```
## YYYY-MM-DD — Short title

**Requested:**

> The request, quoted verbatim. When several prompts shaped one change,
> each is quoted as its own blockquote.

**Changed:**

- A bulleted summary of what was actually done.

**Commit:** `abc1234`
```

- **Heading** — the date the change landed, plus a short title.
- **Requested** — the prompt(s) that asked for the change, in the
  requester's own words. *Pre-record* entries use **Intent** instead — a
  paraphrase, since no prompt was on record.
- **Changed** — what was done, as a bulleted summary.
- **Commit** / **Commits** — the hash(es) the change shipped in. Closely
  related commits may be grouped into a single entry, with every hash
  listed.

**Verification-only entries.** Some entries record a check rather than a
change — a smoke-test, a manual QA pass. These swap **Changed** for a
**Verified** block summarizing what was exercised and the outcome, and
their **Commit** line reads `none — verification only` (optionally naming
the commit that was exercised).

**Decision entries.** Some entries record a decision reached in discussion
that produced no code — keeping a constraint, choosing not to build
something, settling on an approach. These swap **Changed** for a
**Decision** block stating the outcome and the reasoning behind it, and
their **Commit** line reads `none — decision only`.

Newest entries go at the top; the "Pre-record history" block stays at the
end.

A change is logged in the commit **immediately after** the one that makes
it: an entry cites its change's commit hash, and a commit cannot contain
its own hash. So the workflow is — commit the change, then commit the
DEVLOG entry citing that hash. The DEVLOG-entry commit is not itself
logged.

---

## 2026-05-26 — State-only zip picker + select-all / clear-all shortcuts

**Requested:**

> Whoops, apparently I didn't test enough. Let's change it so that if
> you know the state but don't know the county so you leave it at
> 'Any' then when you go to the zipcode list you should see all
> zipcodes for that state. Also, can we add a 'Shift-*' to select all
> and a 'Shift-0' to de-select all?

**Changed:**

- The zipcode checkbox list now activates as soon as a state is
  picked. With county still at "Any", the list contains every zip in
  the state; picking a county narrows it; clearing the county falls
  back to the state-wide list.
- `/api/zipcodes` accepts a new `state_id` param alongside the
  existing `county_ids`. When `state_id` is set without `county_ids`,
  the controller expands to every county in that state and returns
  their combined zips.
- `/api/polls/search` accepts a new `stateId` param. The geo-filter
  fallback chain is now zipcodes → countyId → stateId → none.
- Frontend keyboard shortcuts on the zipcode picker container: `*`
  (or `Shift+8` by code, layout-tolerant) selects every visible zip;
  `Shift+0` (or `)` by character) clears the selection. The picker
  is focusable (`tabindex="0"`) so keys fire when the user clicks
  into the list.
- Hint text updated: "Shift+click for range • * select all • Shift+0
  clear".
- Verified: `?state_id=48` returns 563 WA zips; bare `?stateId=48`
  search returns the 3 WA polls; an Alabama search returns 0.

**Commit:** `c8cb321`

---

## 2026-05-26 — Multi-zip checkbox picker with shift-click range select

**Requested:**

> Can we make the zipcode selectList be a multiple selectList using
> checkboxes such that the user can select a zipcode via checking the
> box and then if the user wants to select the checked zipcode plus
> the next 5 zipcodes the user would use 'Shift-Click' to select all
> 6 zipcodes? If the user wanted to select 3 out of 10 zips then the
> user would just check the checkbox next to the desired zips?

**Changed:**

- Replaced the single zipcode `<select>` on `PollSearchView` with a
  scrollable checkbox list (one row per zipcode in the chosen
  county). Plain click toggles a single zip; shift+click toggles
  every zip between the last single click and the new one,
  inclusive. The toggle direction matches the new state of the
  shift-clicked checkbox — shift-click on an unchecked → adds range;
  shift-click on a checked → removes range.
- A small "Shift+click to select a range" hint renders beneath the
  list when there's more than one zip available.
- Empty/disabled states surface a hint instead of an empty box:
  "Pick a county first" until a county is chosen; "No zipcodes
  seeded for this county" when the county returns zero.
- `/api/polls/search` now accepts repeated or comma-separated
  `zipcode` values, matched as a set. Frontend joins the picked zips
  with commas, mirroring the existing `/api/zipcodes` call pattern
  in `ZipSetter.vue`. The `countyId` fallback for the
  "no zip ticked, county set" path is preserved.
- Verified by replay: `?zipcode=98264` still returns 3 polls
  (single-zip backwards compat); `?zipcode=98220,98225,98264` also
  returns 3 polls; out-of-state zips with no coverage return 0;
  title-only filter still works without geo.

**Commit:** `6e6eb1b`

---

## 2026-05-26 — "Any zipcode" under a chosen county now filters by that county's zips

**Requested:**

> It looks like currently if state and county are populated and
> zipcode set to 'ANY' then zipcode ignores state and county filters
> interpreting 'ANY' as literally any zipcode period instead of
> interpreting 'ANY' as any zipcode from state AND county set of
> zipcodes. Can we change the behavior to the latter?

**Changed:**

- `/api/polls/search` accepts a new optional `countyId` query param.
  The controller resolves a geo filter as: specific zipcode if set,
  else the county's zipcode set if `countyId` is set, else no geo
  filter. All three poll-type loops now match against the resolved
  set rather than the single-zipcode string.
- `PollSearchView` sends `countyId` only when the user has picked a
  county but left zipcode at "Any zipcode". When they pick a specific
  zipcode, only `zipcode` is sent (and the backend's specific-zipcode
  branch wins anyway).
- This refines the earlier option (1) decision: state and county are
  still pure UI scaffolding for narrowing the zipcode dropdown, but
  county *also* becomes a filter when the user leaves zipcode at
  "Any". State alone still has no filtering effect — county is
  required because the cascade keeps the zipcode dropdown disabled
  until a county is picked.
- Verified by replay: `?countyId=3006` (Whatcom) returns all 3 polls
  that cover Whatcom zips; `?countyId=2970` (Adams) returns only
  Electric Cars, which covers Adams zips like 99105.

**Commit:** `58f77f5`

---

## 2026-05-26 — Cascading state/county/zipcode dropdowns on /polls/search

**Requested:**

> for the /polls/search page can we add state and county selectList
> boxes before zipcode where if the state box is populated the county
> selectList depends on what's selected in the state list and zipcodes
> in the zipcode selectList depend on what's selected in the state and
> county selectLists?

> I think (1) is optimal.

(After clarification: option 1 — the dropdowns narrow the zipcode
list only; only the zipcode selection actually filters search
results.)

**Changed:**

- Replaced the freeform zipcode input + datalist on `PollSearchView`
  with three cascading `<select>` boxes: state → county → zipcode.
  Counties stay disabled until a state is chosen; zipcodes stay
  disabled until a county is chosen. Each level resets the
  downstream selections.
- Existing geography endpoints (`/api/states`,
  `/api/counties?state_id=`, `/api/zipcodes?county_ids=`) carry the
  cascade; no new backend endpoints needed.
- Per option (1), only the zipcode value is sent as a search param.
  State and county are UI scaffolding; the backend search filter
  semantics didn't change.
- Cleaned up the now-unused `zipcodes` field from
  `/api/polls/search/suggestions` since the cascade renders the
  county's full known zip list rather than a global autocomplete.

**Commit:** `1951215`

---

## 2026-05-26 — "See closed polls too" filter on /polls/search

**Requested:**

> For the /polls/search page can we add a checkbox (in the heading of
> the 'view results' column maybe) labeled 'See closed polls too' so
> the users can see results for polls from history?

**Changed:**

- `/api/polls/search` accepts a new `includeClosed` query param
  (defaults to false). When true, the controller pulls each type's
  `findExpiredX(now)` alongside `findActive(now)`.
- Result ordering now puts active polls first (closes-soonest
  ascending) and closed polls at the bottom (most-recently-closed
  first), so the active set isn't displaced when the filter is on.
- `PollSearchView.vue` picks up a "See closed polls too" checkbox in
  the existing filter grid — placed in the form rather than the
  actions-column header (which the user suggested) because the table
  only renders after a search, and the column-header placement would
  hide the toggle on the user's first query.
- Closed rows render with muted text (`text-slate-500`) and a small
  "Closed" badge next to the title. The Vote action hides for
  authenticated users on closed rows since there's nothing to vote
  on; View Results stays.

**Commit:** `ccd5289`

---

## 2026-05-26 — Allow past close dates on re-publish, keep 5-day confirmation

**Requested:**

> The questionaire now saves, well done, but when I click the
> 'Publish' button I get the error that close date needs to be in the
> future. While that is a valid constraint for poll creation, after
> the poll instance exists it should be allowable to be modified to
> the past by a Creator or above. Is that possible?

> Let's go with (a) "keep the 5 day warning active on re-publish"

**Changed:**

- `QuestionnaireService.publish` now gates the
  `"Close date must be in the future"` check on
  `existing.submitDate == null`. First publish still rejects past
  close dates; re-publish (after archive + restore) accepts them.
- `ElectionService.publish` and `BallotMeasureService.publish` carry
  the same relaxation, but neither entity has a publish-time tracker
  (`date_submitted` defaults to draft-creation time). For both, the
  re-publish signal is "this poll has cast responses": Election via
  `candidateResponses` across its candidates, BallotMeasure via
  `BallotResponseRepository.findByMeasureId`. Wired the latter in;
  the candidate one was already injected from the prior fix.
- The 5-day-out `close_date_short:` confirmation still fires on every
  publish — a past close date counts as "less than 5 days out", so
  the existing `t('form.closeDateShort', { date })` prompt asks the
  creator to confirm before the poll publishes already-closed.
- Edge case: an Election or BallotMeasure that was published, never
  voted on, then archived/restored will still hit the strict
  future-only check on re-publish. Acceptable since the poll has no
  voter history to wind down; pick a near-future date as workaround,
  or use Super Admin → Manage All Polls.
- Verified by replay: re-publishing Vaccines (which has 26 responses)
  with a past close date returns 422 `close_date_short:` on
  `confirmed=false`, then 200 on `confirmed=true`, with status
  `PUBLISHED`. A fresh first-publish draft with a past close date
  still gets 400 even with `confirmed=true`.

**Commit:** `4fe6f29`

---

## 2026-05-26 — Save Changes on restored-from-archived drafts no longer trips response FKs

**Requested:**

> When attempting to 'Save changes' on the Questionaire with Title =
> 'Vaccines' after archiving it and selecting 'Edit' and updating the
> 'Close date' to 5/24/2026 (2 days ago) I get the sql error 'could
> not execute statement [ERROR: update or delete on table "questions"
> violates foreign key constraint "question_responses_question_id_fkey"
> on table "question_responses" Detail: Key (id)=(100) is still
> referenced from table "question_responses".]', can we fix this?

**Changed:**

- `QuestionnaireService.replaceQuestions` and
  `ElectionService.replaceCandidates` both did a blind
  `deleteAll + saveAll` on every PUT, tripping the
  `question_responses` / `candidate_responses` FK when a previously
  PUBLISHED poll had been archived, restored to DRAFT, and re-saved.
  Both helpers now compare the incoming list against existing rows and
  return early when they match — so close-date-only edits no longer
  touch the child tables.
- When the incoming list differs from existing AND any existing child
  row has responses, both helpers now throw `409 Conflict` with a
  friendly message pointing the creator at Super Admin → Manage All
  Polls. The voter-intent constraint from the
  [[project_published_poll_edits]] decision is enforced at the layer
  that actually executes the change.
- Wired `QuestionResponseRepository` into `QuestionnaireService` and
  `CandidateResponseRepository` into `ElectionService` for the
  existence checks.
- Verified by replay: the exact PUT payload that previously errored
  now returns 200; editing a question text → 409 with the friendly
  message; adding a question → same 409. DB row counts intact
  through both rejected attempts.

**Commit:** `badd417`

---

## 2026-05-26 — Profile-conditional JavaMailSender bean (Mailpit in dev, SendGrid otherwise)

**Requested:**

> Regarding your message: "The user's real magic-link login flow is
> currently broken — they'd hit it the next time they try to sign in
> via the email link. Worth either fixing the SendGrid creds, pointing
> spring.mail.host at Mailpit (port 1025), or adding a profile-conditional
> bean swap", the bean swap would be my preference.

**Changed:**

- Added `com.pollsystem.email.MailConfig` with two `@Profile`-gated
  `JavaMailSender` beans. Active profile `local` → Mailpit at
  localhost:1025 (no auth, no STARTTLS); any other profile → SendGrid
  relay reading `SENDGRID_API_KEY` from the environment.
- Set `spring.profiles.default: local` in `application.yml` so a bare
  `java -jar` in dev defaults to Mailpit. Deployed envs set
  `SPRING_PROFILES_ACTIVE` explicitly to get the SendGrid bean.
- Removed the `spring.mail.*` block from `application.yml` and deleted
  `application-local.yml` — both now redundant since the active mail
  backend is wired in `MailConfig.kt`.
- Verified by restart: log line `No active profile set, falling back
  to 1 default profile: "local"`, and a fresh
  `POST /api/auth/magic-link/request` lands in Mailpit (count 2 → 3,
  no `Authentication failed` warning).

**Commit:** `0573105`

---

## 2026-05-26 — Fix publish-drops-edits bug; add super-admin safe-field edits

**Requested:**

> Regarding the /creator/dashboard, I wanted to edit the 'Close date'
> for a questionaire I'd created so I clicked 'Archive' and edited the
> 'Close date' to May 25, 2026, published the draft but change did not
> take and there was no warning message, can that be fixed so I at
> least get a warning message? It'd be great if as a 'Super Admin' I
> could modify any part of a poll.

> fix the bug and add super-admin safe-field edits

**Changed:**

- Fixed `publish()` in `QuestionnaireForm.vue`, `ElectionForm.vue`, and
  `BallotMeasureForm.vue`: it now calls `saveDraft()` unconditionally
  (was gated on `draftId == null`, so existing-draft edits were never
  PUT before publish). Publish aborts if save surfaced an error,
  letting the user see "Close date must be in the future" and other
  validation messages instead of a silent no-op.
- Added a super-admin `Manage All Polls` page at `/super/manage-polls`,
  backed by a new `SuperPollsController` (`/api/super/polls`). Lists
  every poll across all creators and statuses; inline-edit lets a
  super-admin change **title, summary, and closeDate** on any poll —
  the fields documented as safe to mutate after publication.
- Voter-facing structure (questions, candidates, geo scope) remains
  off-limits even to super-admin: changing it after responses are cast
  would silently re-shape what voters said. The constraint from the
  earlier "editing published polls" decision is preserved; this is a
  safe-field carve-out, not a reversal.
- Dashboard tile + i18n keys added under `super.dashboard` and
  `super.managePolls`. Other locales fall back to en via the existing
  `fallbackLocale: 'en'`.

**Commit:** `bfec7a8`

---

## 2026-05-26 — Restore zipcode autocomplete on the search form

**Requested:**

> Regarding the /polls/search component, would it be possible to make
> the 'Title contains' field autocomplete as the user types. Same with
> the zipcode field and the 'Candidate name' field?

> It looks like only the zipcode field is missing autocomplete, can you
> make it work like the 'Candidate name' field?

**Changed:**

- Added a `zipcodes: List<String>` field to `SearchSuggestions` and
  populated it in `/api/polls/search/suggestions` with the distinct
  zipcodes drawn from currently-active questionnaires (via
  `QuestionnaireDomain`), elections, and ballot measures, sorted.
- Wired the zipcode `<input>` in `PollSearchView.vue` to a
  `<datalist id="zipcode-suggestions">` populated from
  `suggestions.zipcodes`, mirroring the candidate-name field exactly
  (`list=` + `autocomplete="off"`).
- Smoke-tested headlessly: 563 distinct 5-digit zips returned, sorted
  ascending, all distinct; the Vite dev server proxies the new field;
  searching by a suggested zip narrows results as expected.

**Commit:** `e6f816d`

---

## 2026-05-15 — Re-smoke-test the OR search after backend restart

**Requested:**

> smoke-test the OR search

**Verified:**

- Re-ran the 9-case OR-search suite against the backend after a restart:
  no-filter baseline, title-only, candidate-only, title-OR-candidate
  union, the OR result narrowed by `type` (both directions), a no-match
  query, and mixed title/candidate hits.
- All 9 passed, identical to the post-implementation run — OR semantics,
  blank-field drop-out, and the `type` AND-filter all intact.

**Commit:** none — verification only; exercised `46469e9`.

---

## 2026-05-15 — Track .vscode settings and frontend lockfile

**Requested:**

> commit the .vscode and package-lock files

**Changed:**

- Committed the previously-untracked `.vscode/settings.json` (a Java
  extension build-config setting) and `frontend/package-lock.json` (npm
  lockfile, `lockfileVersion 3`) so the dependency tree is pinned for
  everyone working in the repo.

**Commit:** `051a97d`

---

## 2026-05-15 — Enforce DEVLOG logging: CLAUDE.md + Stop hook

**Requested:**

> Also log decisions that didn't result in code and how do I ask for this
> type of logging to be "automatic"?

> Both, and backfill the three decisions

**Changed:**

- Added `CLAUDE.md` instructing Claude to add a DEVLOG entry after every
  commit and to log no-code decisions as Decision entries (the standing
  instruction layer).
- Added `.claude/hooks/devlog-reminder.sh` and a `Stop` hook in
  `.claude/settings.json` that flags any change commit with no
  corresponding `docs/DEVLOG.md` entry (the enforcement layer).
- Related earlier commits: the "Decision entries" format variant
  (`a0710ba`) and the three backfilled no-code decisions (`a6243d6`).

**Commit:** `d6ceeac`

---

## 2026-05-15 — Document verification-only DEVLOG entries

**Requested:**

> sure, go ahead and log it please.

…approving the offer to cover the verification-only entry variant in the
format section.

**Changed:**

- Added a "Verification-only entries" paragraph to the "Entry format"
  section, describing entries that record a check rather than a change:
  they swap **Changed** for a **Verified** block and use a
  `none — verification only` commit line.

**Commit:** `31ff540`

---

## 2026-05-15 — Smoke-test the OR search

**Requested:**

> smoke-test the OR search

**Verified:**

- Ran 9 query cases against the running backend exercising the OR
  semantics of the title and candidate-name filters: no-filter baseline,
  title-only, candidate-only, title-OR-candidate (union), the OR result
  narrowed by `type` (both directions), a no-match query, and mixed
  title/candidate hits.
- All 9 passed — notably the union case (a title-matching questionnaire
  and a candidate-matching election both surface) and the no-match case
  (empty result, not a false match-all).

**Commit:** none — verification only; exercised `46469e9`.

---

## 2026-05-15 — Fix the DEVLOG entry-format workflow wording

**Requested:**

> Yes, fix the format wording

**Changed:**

- The "Entry format" section claimed a change is "normally logged in the
  same commit that makes it" — not achievable, since an entry cites its
  change's commit hash and a commit cannot contain its own hash.
- Reworded it to the actual workflow: commit the change, then commit the
  DEVLOG entry citing that hash; the DEVLOG-entry commit is not itself
  logged.

**Commit:** `7d32312`

---

## 2026-05-15 — Add a README with a documentation index

**Requested:**

> Add DEVLOG to README so contributors find it

**Changed:**

- Created `README.md` at the repo root (the repo had none): a project
  summary, a backend/frontend stack overview, and a documentation index
  table linking every file under `docs/`.
- `docs/DEVLOG.md` is the first row of that index and is called out again
  under "Contributing" as a same-commit expectation for new changes.

**Commit:** `24b2717`

---

## 2026-05-15 — Establish a development log

**Requested:**

> I really love your summaries of what changed and happened, is there a way
> my prompts could be included in the transcript so as to provide a bit of
> added context as to what I was asking for?

> Yes, please start option 2

> Reconstruct earlier session entries from git log please.

> Might as well add the commits from 2026-05-10 and prior to the record in
> the format you described and commit please.

**Changed:**

- Created `docs/DEVLOG.md`, a running log pairing each change's request (in
  the requester's own words) with a summary of what was done and the
  commit hash; newest entries on top.
- Seeded it with the two then-current poll-search changes, then
  reconstructed 16 prior-session entries (2026-05-13 → 2026-05-11) quoted
  from the conversation record.
- Added a "Pre-record history" section covering every commit from the
  2026-05-07 bootstrap through 2026-05-11, paraphrased from commit history.

**Commits:** `8db9f16`, `37bbc44`

*(The commit that adds this very entry is not itself logged — it only
appends these lines.)*

---

## 2026-05-15 — Poll search: OR-combine title and candidate-name filters

**Requested:**

> Would it be possible to change the /polls/search form so that instead of
> matching on ('Title contains' AND 'Candidate name' AND 'Type' (nullable))
> perhaps match on ('Title contains' OR 'Candidate name' AND 'Type'
> (nullable))?

**Changed:**

- `PollSearchController.search()` now OR-combines the title and
  candidate-name filters: a poll matches if its title hits **or** its
  candidate roster hits, instead of requiring both.
- Previously a candidate-name query silently excluded every questionnaire
  and ballot measure; now a title-matching poll of any type still surfaces
  alongside candidate-matching elections.
- A blank field drops out of the OR (so title-only and candidate-only
  searches behave as before). New `titleHit()` helper enforces "a blank
  query is not a hit."
- Type and zipcode remain AND constraints — they narrow whatever the
  text search returns.

**Commit:** `46469e9`

---

## 2026-05-15 — Poll search: title & candidate autocomplete

**Requested:**

> Can we get auto-complete/suggestion for the /polls/search 'Title
> contains', zicode and Candidate textboxes?

…and, after discussion of the zipcode list's breadth:

> Let's just not do auto-complete/suggestion for the 'zipcode' textbox

**Changed:**

- Added `GET /api/polls/search/suggestions`, returning distinct titles and
  candidate names drawn only from currently-active polls.
- `PollSearchView.vue` fetches the suggestions on mount and exposes them as
  native `<datalist>` hints on the Title and Candidate fields.
- The zipcode field was left as a plain input: its suggestion set spans
  whole questionnaire domains (~560 zips) and was too broad to be useful.
- Whitelisted `/api/polls/search/**` in `SecurityConfig` so the new
  sub-path stays public.

**Commits:** `4e53bca`

---

## 2026-05-13 — COSTS.md: database encryption + staging-as-production reframe

**Requested:**

> Can we add 'database encryption' to the docs/COSTS.md scenarios for the
> staging and production costs?

> Essentially my current inclination is to use staging as 'production' in
> effect then if I get to 1000 or more subscriptions add a more robust true
> production tier.

**Changed:**

- Added a database-encryption-at-rest line ($0, default-on AES-256) to both
  the conservative-baseline and lowest-cost-path staging tables in
  `docs/COSTS.md`.
- Reframed the lowest-cost staging configuration as "staging-as-production
  for the early phase," and added `≥ 1,000 paid subscriptions` as the top
  trigger for moving to a true production tier.
- Noted that the production-tier step is where encryption upgrades to
  customer-managed keys (BYOK / AWS KMS, ~$1/key/month) as a compliance
  lever.

**Commit:** `020e713`

---

## 2026-05-13 — i18n: Japanese locale

**Requested:**

> Let's add Japanese too

**Changed:**

- Added `frontend/src/i18n/ja.json` and registered the `ja` locale
  (日本語) in `i18n/index.ts`. Key-parity with `en.json` verified.

**Commit:** `2201461`

---

## 2026-05-13 — i18n: Norwegian (Bokmål) locale

**Requested:**

> Can we add Norwegian too?

**Changed:**

- Added `frontend/src/i18n/nb.json` and registered the `nb` locale
  (Norsk) in `i18n/index.ts`. Key-parity with `en.json` verified.

**Commit:** `a411310`

---

## 2026-05-13 — i18n: Italian & Brazilian Portuguese locales

**Requested:**

> I like it. Can we add Italian and Portuguese?

**Changed:**

- Added `frontend/src/i18n/it.json` and `pt-BR.json`, registering the `it`
  (Italiano) and `pt-BR` (Português) locales in `i18n/index.ts`.
  Key-parity with `en.json` verified.

**Commit:** `3e0ea5d`

---

## 2026-05-12–13 — Full app internationalization (en/fr/es/de/zh-CN)

**Requested:**

The initiating request to internationalize the app is not in the
reconstructable record; the work was carried out in four reviewable
phases, with these continuation prompts:

> proceed with phase 2

> proceed with phase 3

> Yes please *(phase 4)*

**Changed:**

- Stood up `vue-i18n@10`: `i18n/index.ts` with `SUPPORTED_LOCALES`,
  `setLocale()`, browser-locale detection and `localStorage` persistence;
  `templateLabel.ts` helper for localized template-JSON labels; language
  switcher in `App.vue`.
- Localized the whole app across four phases — Phase 1: election voting
  form; Phase 2: rest of the voter journey; Phase 3: creator dashboard,
  authoring forms, ZipSetter, creator-request views; Phase 4: admin +
  super views.
- Locale files for `en`, `fr`, `es`, `de`, `zh-CN` (later joined by `it`,
  `pt-BR`, `nb`, `ja`), each at key-parity with `en.json`.

**Commits:** `c0e79c9` (Phase 1), `5a51366` (Phase 2), `a5b7f63` (Phase 3),
`edabf87` (Phase 4)

---

## 2026-05-12 — Election voting form: candidate widget rendering hints

**Requested:**

> Given the template located at super/poll-templates and how election
> candidates are rendered at /polls/election/1, would it be doable to add a
> name : value pair to the 'candidates' json before the item element …
> widget : selectOne …

> Yes, the candidates should be grouped by office, perhaps we should put
> the officeName after widget … I was hoping a 'widget : selectOne' would
> be rendered as a select one radio buttons group, a widget of type
> 'widget : selectOneList' would be rendered as dropdown select …

> Yes, let's go with that and let's not forget the selectOneCheckbox also.

**Changed:**

- Added `widget` and `groupBy` rendering hints to the Election template's
  `candidates` block via migration `V12` — five widget types
  (`selectOneRadio`, `selectOneList`, `selectOneCheckbox`,
  `selectManyCheckbox`, legacy `selectOne`) and `groupBy: officeName`.
- `ElectionService` parses the hints from the template JSON;
  `ElectionDto` carries `candidatesWidget` / `candidatesGroupBy`.
- `ElectionResponseForm.vue` rewritten to branch on the widget type, with
  per-office selection state grouped under each office name.

**Commits:** `366f7eb` (template + migration), `c0e79c9` (form rendering)

---

## 2026-05-11 — Creator dashboard: relabel Delete → Archive, highlight past close dates

**Requested:**

> Can we change the background-color of a cell in the 'Close date' column
> whose close date is in the past or today?

> /btw, I'd prefer a light orange

> Do you think on the /creator/dashboard page we should stick with 'Delete'
> or go with 'Archive' or 'Disable' for the action label?

**Changed:**

- `DashboardView.vue` highlights close-date cells that are past or today in
  light orange (`bg-orange-50 text-orange-900`) via a `closeDatePast()`
  helper.
- Relabeled the soft-delete action column from "Delete" to "Archive" so the
  label matches the actual (reversible) behavior.

**Commit:** `8920826`

---

## 2026-05-11 — Surface ResponseStatusException reasons in JSON errors

**Requested:**

> When I restore an archived poll, edit it, save changes and click the
> 'Publish' button it says 'Publish failed'.

**Changed:**

- Set `server.error.include-message: always` in `application.yml` so the
  `reason` from a `ResponseStatusException` reaches the JSON error body,
  turning opaque "Publish failed" messages into actionable ones.

**Commit:** `cc73971`

---

## 2026-05-11 — Creator dashboard: show-archived toggle + restore

**Requested:**

> Before I delete it, is there a way to bring it back, like maybe an action
> link at the top of the /creator/dashboard that says 'Show archived'?

> restore-to-DRAFT feels right, make it so #1.

**Changed:**

- Added `POST /{type}/{id}/restore` (ARCHIVED → DRAFT) and a `?showArchived`
  query param to `CreatorPollsController`.
- `DashboardView.vue` gained a "Show archived" toggle and per-row Restore
  buttons; ownership is enforced server-side.

**Commit:** `f120dcc`

---

## 2026-05-11 — Creator dashboard: soft-delete (Archive) column

**Requested:**

> Is there way to get a 'Delete' column as the last column of the
> /creator/dashboard and when pressed the poll will be deleted?

> Yes, I concur, let's go with a soft-delete please.

**Changed:**

- Added `DELETE /{type}/{id}` to `CreatorPollsController`, implemented as a
  soft-delete that flips the poll to `PollStatus.ARCHIVED` rather than
  removing the row; creator ownership is checked.
- `DashboardView.vue` gained the action column.

**Commit:** `1ca9981`

---

## 2026-05-11 — Poll search: drop Creator email filter and column

**Requested:**

> Can we remove the 'Creator Email' textbox and the 'Creator' column in the
> results section of the /polls/search page?

**Changed:**

- Removed the Creator-email filter input and the Creator results column
  from `PollSearchView.vue`.

**Commit:** `3b46f8d`

---

## 2026-05-11 — JWT: extend session TTL to 90 days

**Requested:**

> Can we implement the 'Long-lived sessions' option?

**Changed:**

- Set `app.jwt.expiration-ms` to 90 days (`7776000000`) so authenticated
  sessions persist far longer between magic-link logins.

**Commit:** `33558bf`

---

## 2026-05-11 — Router guard: hydrate user from token before deciding auth

**Requested:**

> When I press [Shift - Enter] to refresh a page it puts me at /login, is
> there a way to not do that?

**Changed:**

- Made the Vue Router guard async: when a token is present but the user
  isn't yet loaded, it awaits `authStore.fetchUser()` before deciding,
  so a hard refresh no longer bounces an authenticated user to `/login`.

**Commit:** `6fb171e`

---

## 2026-05-11 — Admin dashboard: refetch on bfcache restore

**Requested:**

> When I flip the request by clicking the button then I go back to the
> dashboard it still lists the 'Action' as what it was before I flipped it.

> I tried to flip creator request id #3 to 'Approve' but when I went back to
> the /admin/dashboard it still said 'Rejected'.

**Changed:**

- `DashboardView.vue` (admin) listens for `pageshow` and refetches when the
  page is restored from the browser's back-forward cache, so a flipped
  decision no longer shows a stale "Action".

**Commit:** `be4cf87`

---

## 2026-05-11 — Creator requests: allow admins to flip a previous decision

**Requested:**

> On the /admin/dashboard 'Recent decisions' section … can we have an
> 'Approve' button if the request is in 'rejected' status otherwise a
> 'Reject' button if the request is in 'Appproved' status?

**Changed:**

- `CreatorRequestService.decide()` no longer requires a PENDING request — it
  accepts any status other than the target decision, allowing an admin to
  flip an already-decided request.
- Flipping APPROVED → REJECTED disables the associated role-assignment rows
  (the user's `access` level is left untouched).
- `CreatorRequestDetailView.vue` shows Approve unless already APPROVED, and
  Reject unless already REJECTED.

**Commit:** `9188524`

---

## 2026-05-11 — Decision: PUBLISHED polls stay non-editable

**Requested:**

*The prompt that opened this discussion is not in the reconstructable
record; the decision is recovered from project notes. (Approximate date.)*

**Decision:**

- PUBLISHED polls cannot be edited — the Edit affordance stays gated on
  `status === 'DRAFT'`. A creator who needs to revise a published poll
  archives it and creates a fresh draft.
- Reasoning: editing a live poll would silently corrupt voter intent — if
  question text or candidate names change after votes are cast, existing
  responses no longer mean what voters said. A "safe-fields-only" partial
  edit (title, close date) was considered and rejected as UI complexity
  for a rare case.

**Commit:** none — decision only.

---

## 2026-05-11 — Decision: no user_history table

**Requested:**

> Should we have a user_history table to track changes in user.access …
> if so it should probably have a created timestamp field.

> Oh, good to know, let's leave it as is.

**Decision:**

- No dedicated `user_history` table. A user's access-level history is
  derivable from existing `role_assignment` rows, so a separate audit
  table would duplicate recoverable state for no real gain.

**Commit:** none — decision only.

---

## 2026-05-11 — Decision: keep email and phone both UNIQUE

**Requested:**

> In your opinion, should we keep the constraint that each email address
> and each phone number needs to be unique or should we think about just
> email or just phone number or combine it with zipcode or?

> let's make phone unique, relax email

> I think we should just leave it like it is. I'll risk locking out shared
> inboxes.

**Decision:**

- The `users` table keeps **both `email` and `phone` UNIQUE**. Relaxing
  email uniqueness (to allow shared household inboxes) was briefly favored,
  then rejected.
- Reasoning: magic-link auth looks accounts up by email, so a non-unique
  email would make that lookup ambiguous and force a login-flow redesign
  (asking for phone as well). The user accepted occasionally locking out
  shared inboxes to keep login simple.

**Commit:** none — decision only.

---

## 2026-05-11 — Questionnaire response: Yes/No radios

**Requested:**

> Nicely done … change the 'Answer' section from a textarea to a set of
> Yes/No radio buttons?

**Changed:**

- `QuestionnaireResponseForm.vue` replaced the free-text Answer textarea
  with a Yes/No radio-button pair per question (`name="q-${q.id}"`).

**Commit:** `fe5d07ba`

---

# Pre-record history

The entries below predate the available conversation record. Their
**Intent** lines are paraphrased from commit history, not quoted from any
prompt. Closely related commits are grouped; every commit hash is listed.

---

## 2026-05-11 — Poll search: multi-zipcode scope popover

**Intent:** Keep the search results table compact when a poll covers many
zipcodes.

**Changed:**

- `PollSearchView.vue` shows the first zipcode inline and collapses the
  rest behind a `…` popover that closes on outside-click or Esc.

**Commit:** `9a6b6be`

---

## 2026-05-11 — ZipSetter: collapsible disclosure drawers

**Intent:** Tame the ZipSetter UI by letting the county and zipcode lists
collapse.

**Changed:**

- Counties and zipcodes are presented in collapsible disclosure drawers
  rather than two long flat lists.

**Commit:** `e38ebac`

---

## 2026-05-11 — Creator dashboard: hide "Request admin access" for ADMIN+

**Intent:** Don't offer an admin-access request to users who already have
it.

**Changed:**

- The "Request admin access" affordance is hidden when the user is already
  ADMIN or SUPER.

**Commit:** `3cefcde`

---

## 2026-05-11 — Admin dashboard: show all in-scope unassigned requests

**Intent:** Surface every unassigned request in an admin's scope, not just
the stale ones.

**Changed:**

- The admin dashboard lists all in-scope unassigned requests instead of
  filtering down to only stale ones.

**Commit:** `f3e6994`

---

## 2026-05-10 — Admin & Super request-management views

**Intent:** Build out the request-handling surface for admins and supers.

**Changed:**

- Added the admin dashboard, the creator-request detail view, and the
  Super admin-workload table.
- Migration `V11` seeds a local-dev ADMIN scoped over LA County zipcodes
  so the views have data to exercise.

**Commits:** `79eb1b4`, `ae04604`

---

## 2026-05-10 — EmailService: real delivery via JavaMailSender

**Intent:** Actually send mail instead of stubbing it.

**Changed:**

- `EmailService` sends through `JavaMailSender` rather than logging or
  no-op'ing the message.

**Commit:** `c3a46f5`

---

## 2026-05-10 — Creator/Admin request: optional reason field

**Intent:** Don't force a reason when submitting a creator or admin request.

**Changed:**

- The reason field on both the Creator-Request and Admin-Request forms is
  now optional.

**Commits:** `fda7251`, `196ba0a`

---

## 2026-05-10 — ZipSetter: select-all checkboxes

**Intent:** Make bulk county/zipcode selection quicker.

**Changed:**

- Added Select-all checkboxes for counties and, separately, for zipcodes.

**Commits:** `c54fe7f`, `282ed50`

---

## 2026-05-10 — Seed full US county & ZIP data

**Intent:** Replace partial geographic seed data with the full US set.

**Changed:**

- Seeded the complete US counties list and the full ZIP-to-county mapping;
  the empty-county case is surfaced in ZipSetter.

**Commits:** `adb1806`, `1d52a47`

---

## 2026-05-10 — ZipSetter cascade fix

**Intent:** Fix the county→zipcode cascade not updating reliably.

**Changed:**

- Switched the ZipSetter cascade from implicit reactivity to an explicit
  `@change` handler.

**Commit:** `2ca0f9d`

---

## 2026-05-10 — Adopt Tailwind v4 and migrate all views

**Intent:** Standardize styling on Tailwind.

**Changed:**

- Adopted Tailwind v4 as the default styling tool and migrated the
  remaining 23 views and components to it.

**Commits:** `e6f4f39`, `67cd70f`

---

## 2026-05-10 — Public poll search + HomeView action cards

**Intent:** Let visitors search polls without signing in, and give the home
page clear entry points.

**Changed:**

- Made poll search publicly accessible and added action cards to
  `HomeView`.

**Commit:** `c866389`

---

## 2026-05-10 — Local dev: magic-link wiring, Mailpit, error dispatches

**Intent:** Make the magic-link flow runnable end-to-end locally.

**Changed:**

- Wired the frontend to magic-link auth and added a Mailpit local-dev
  profile for catching outbound mail.
- Permitted `ERROR`/`FORWARD` dispatches in the security config so real
  HTTP status codes surface instead of being masked.

**Commits:** `7fbcba6`, `40e5493`

---

## 2026-05-10 — Build & deploy docs, Gradle wrapper/toolchain

**Intent:** Make the build reproducible and document local deployment.

**Changed:**

- Committed the Gradle wrapper and added a JDK 17 toolchain with documented
  JVM compatibility.
- Expanded `DEPLOYING-LOCAL.md` (smoke-test section, local profile,
  `gradlew`), updated `BUILDING.md`, and dropped an unused
  `tsconfig.node.json` project reference.

**Commits:** `ede790d`, `40e5493` *(see above)*, `a427faa`, `6c626c2`,
`bc57a63`, `3c68e21`

---

## 2026-05-09 — Magic-link auth migration + Stripe webhooks

**Intent:** Move authentication to passwordless magic links and accept
Stripe billing events.

**Changed:**

- Migrated auth to a magic-link flow and added a Stripe webhook handler.
- Documented magic-link auth, Stripe billing, and the Fly.io deploy.

**Commits:** `cb80bbb`, `45ddb61`

---

## 2026-05-09 — Test-suite stabilization

**Intent:** Get the full test suite reliably green.

**Changed:**

- Fixed transaction-isolation, validation, and a demote bug to make the
  suite pass; switched the test base to a singleton-container pattern for
  stability.
- Quoted the `Office.desc` column to avoid a Postgres reserved-word
  collision.

**Commits:** `0bcc8e6`, `ca38255`, `09495cb`

---

## 2026-05-09 — Docs: rename COSTS-SPRING → COSTS

**Intent:** Tidy documentation naming.

**Changed:**

- Renamed `COSTS-SPRING` to `COSTS`.

**Commit:** `3a59e52`

---

## 2026-05-07 — Project bootstrap

**Intent:** Seed the repository.

**Changed:**

- Initial commit (`convo.txt`).

**Commit:** `5a631ef`
