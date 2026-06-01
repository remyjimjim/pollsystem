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

## 2026-05-31 — onlyPurview filter on the Results pages

**Requested:**

> tell me if this is your understanding, when a person registers then
> they become a "USER" and the email address is their identifier and
> their phone number is stored and the zipcode they enter is stored
> in the user table but doesn't limit what polls you can complete, it
> only comes into play when the polls results are presented because
> let's say there's a county questionnaire and you want to see the
> results so the results will show for users whose zipcode is within
> the purview of the poll then there will be a checkbox to show
> non-county voters results.

> Yes let's do that, with a Note at the top of the results display
> saying 'Below are the results from all respondents. To limit
> results to only those respondents from the intended purview
> (zipcode, county, state or nation) then check the checkbox below
> for "Only voters from poll's purview.".' Currently, all polls are
> only pertinent to the US.

**Changed:**

- Each of the three Results endpoints
  (`/api/polls/{questionnaires,elections,ballot-measures}/{id}/results`)
  accepts an optional `onlyPurview=true|false` (default false). When
  true the response list is intersected with the poll's purview
  zipcode set:
  - Election: `{election.zipcode}`
  - Questionnaire: distinct `questionnaire_domains.zipcode`
  - BallotMeasure: `{election.zipcode}` (via its parent Election)
- The new filter composes with the existing `?zipcode=` filter and is
  reported back in `filterApplied` as `{"onlyPurview": "true"}` when
  active. K-anonymity suppression now triggers when either filter is
  active and the surviving group is below the configured threshold.
- Frontend `PollResultsView.vue` renders the requested note paragraph
  above the filter form, followed by a smaller line
  "Currently, all polls are only pertinent to the US.", and a
  checkbox "Only voters from poll's purview" that re-fetches on
  toggle. Default is unchecked → all respondents.
- New backend test
  `onlyPurview narrows to submitters within the poll's zipcode set`
  builds 3 in-purview + 2 out-of-purview voters and confirms
  `onlyPurview=true` returns 3 with `filterApplied.onlyPurview=true`.
- Verified live: `GET /api/polls/{type}/{id}/results?onlyPurview=true`
  drops out-of-purview rows and `filterApplied` reflects the toggle.

**Commit:** `5c7e45a`

---

## 2026-05-31 — Edit user role + location on /super/manage-users

**Requested:**

> on the /super/manage-users page the super admin should be able to
> change any user's role from what it is to any of USER, CREATOR and
> ADMIN. Same for any user's state, county and zipcode.

> How about if we update each role↔assignment to the role that the
> super-admin specifies?

**Changed:**

- New `PUT /api/super/users/{userId}` endpoint with body
  `{role?, zipcode?}`. Both fields optional; either or both can land
  in one round-trip. Role is restricted to USER / CREATOR / ADMIN
  (LISTABLE_ROLES), so a super can't accidentally make/unmake another
  super here. Zipcode is validated against `county_zips` so the
  state/county always derive cleanly.
- When the role changes, every existing `RoleAssignment` for the user
  is rewritten to the new role via a new
  `RoleAssignmentBulkOps` service that issues a native
  `UPDATE role_assignments SET role = CAST(:role AS access_level)
   WHERE user_id = :userId` and `flush + clear`s the persistence
  context so reads in the same transaction see the new role. The
  in-memory `findByUserId + saveAll(copy(role = …))` path doesn't
  work — Hibernate's merge against a Kotlin `val` data-class copy
  with the same id silently no-ops for an enum field mapped via
  `@JdbcTypeCode(NAMED_ENUM)`. Recorded so the next person doesn't
  retrace the half-day diagnosis.
- Also fixed a subtle bug found in the same diagnosis: after
  `users.save(u.copy(access = newAccess, ...))`, Hibernate's merge
  mutates `u`'s backing field even though Kotlin marks it `val`, so a
  subsequent `newAccess != u.access` check was always false and the
  RA update was skipped. Snapshot `previousAccess` before the save.
- Frontend `ManageUsersView.vue` adds an Edit modal that opens when
  clicking the role badge or any of the State / County / Zipcode
  cells. The modal has a Role select and cascading State → County →
  Zipcode dropdowns; the cascade seeds from the user's current
  zipcode on open. Save PUTs only the fields that changed; cancel
  closes without writing.
- Backend tests: role change propagates onto every RoleAssignment
  via a fresh repo read, zipcode change re-derives state/county,
  SUPER target / unknown zipcode / bad role rejected with the right
  HTTP codes.
- Verified end-to-end against the running backend: PUT body with
  both fields lands; revert restores; 400 on unknown zipcode and on
  role=SUPER.

**Commit:** `8b763ae`

---

## 2026-05-31 — Show-disabled on its own row, Search button next to Notes on Manage Polls

**Requested:**

> Can we move the "Show disabled" checkbox so that it is exactly
> underneath the "Election" Poll type checkbox on the
> /admin/manage-polls

> Can we add a 'Search' button next to the 'Notes' input box on the
> /admin/manage-polls page?

**Changed:**

- "Show disabled" moved out of the Poll Type checkbox flex-wrap row
  onto its own line inside the same fieldset, so it sits directly
  under the Election checkbox above instead of trailing Ballot
  Measure to the right.
- Added a black Search button after the Notes input, mirroring the
  /super/manage-users button. Form `@submit.prevent` now wires to a
  new `searchNow` that cancels every pending debounce timer
  (`fetchTimer`, `titleTimer`, `notesTimer`) and runs `fetchPolls()`
  immediately. Live-filter on each input change is unchanged.
- The Search cell uses the same two-row layout as the Notes cell
  (invisible heading on top) so the button's top edge lines up with
  the top of the Notes input under `items-start`.
- i18n: `admin.managePolls.search`, `searching`.

**Commit:** `7e72fd6`

---

## 2026-05-31 — Creator column + email-on-note on Manage Polls; Search button on Manage Users

**Requested:**

> can we add a black 'Search' button after the 'Messages' input on
> the /super/manage-users page so we can search?

> Can we align the 'Search' button vertically with the 'Messages'
> input box so the top of the 'Messages' box is the same height as
> the top of the 'Search' button?

> Can we make the /admin/manage-polls list the email of the creator
> of the poll and have the 'Notes' results widget behave exactly
> like the /super/manage-users 'Msg' results where the message can
> be up to 2000 characters and you can save the message and
> optionally email the creator a copy of the message?

**Changed:**

- New `V16__poll_note_emailed.sql` adds `emailed BOOLEAN NOT NULL
  DEFAULT FALSE` to `poll_notes` so a created note can record
  whether a copy was emailed to the poll's creator, mirroring the
  `user_messages.emailed` flag from the Messages flow.
- `PollNote` entity, `NoteDto`, and `toDto` thread the new flag
  through.
- `CreateNoteRequest` gains a `sendEmail` boolean; a new
  `EditNoteRequest` (body only) is split out so editing can't be
  asked to re-email. `AdminPollsController.createNote` resolves
  the poll's creator email and calls `EmailService.send` when
  `sendEmail=true`, then saves the note with `emailed=true`.
  Editing never re-emails.
- New backend test
  `createNote with sendEmail emails the poll creator and persists
  the flag` covers the path via a recording `EmailService` test
  config.
- Frontend `ManagePollsView.vue`:
  - New "Creator" column between Type and State, sortable
    (`sortKey: 'creatorEmail'`).
  - The Note modal grows a `noteModalSendEmail` ref; on new mode
    only, a `Email a copy to {email}` checkbox shows. `saveNote`
    posts `{body, sendEmail}` to the new endpoint.
  - The flag resets to false whenever the modal opens, after a
    save, or when toggling back to history.
- Frontend `ManageUsersView.vue`:
  - A black "Search" cell follows the Messages input. Clicking the
    button (or Enter inside any filter input — the form's
    `@submit.prevent` now calls `searchNow`) cancels every pending
    debounce timer and fires `fetchUsers()` immediately. The
    existing live-filter is unchanged.
  - The Search cell carries an invisible heading row so its
    `flex flex-col gap-1` structure matches the Messages cell, and
    the button's top edge lines up with the top of the Messages
    input under the form's `items-start` alignment.
- i18n: `admin.managePolls.colCreator`, `sendToCreator`,
  `super.manageUsers.search`, `searching`.
- Verified live against the running backend: row payloads now
  include `creatorEmail`; a note created with `sendEmail: true`
  lands in Mailpit ("A note about your poll") to the creator, and
  the row's `latestNote.emailed` is `true`; editing the same note
  leaves Mailpit's count unchanged.

**Commit:** `8f4db69`

---

## 2026-05-31 — Sortable Title / Type / Zipcodes / Closes on /polls/search

**Requested:**

> For /polls/search page can we make the following columns sortable:
> 'Title' (alphabetic), 'Type' (alphabetic), 'Zipcodes' (alphanumeric),
> 'Closes' (date/time)?

**Changed:**

- All four columns on the public search table are now click-sortable
  with the same ▲ / ▼ indicator used by `/super/manage-users` and
  `/admin/manage-polls`. Initial render keeps the backend's
  active-first / closed-last ordering; the first header click drops
  into client-side sort.
- Title and Type compare on lowercase strings. Zipcode anchors on the
  first item in the row's already-sorted zipcodes array, which gives
  a stable alphanumeric key without needing a secondary tie-breaker.
  Closes compares ISO timestamps lex-wise; null close-dates sort
  last in ascending order (treated as "never closes").

**Commit:** `5c4eca9`

---

## 2026-05-31 — Rename Manage Polls column header to "Enabled?"

**Requested:**

> For the /admin/manage-polls page, can we change the "Enable/Disable"
> heading to "Enabled?"?

**Changed:**

- `admin.managePolls.colEnable` flipped from "Enable/Disable" to
  "Enabled?". The cell already reads as a yes/no answer (checked =
  enabled) after the prior polarity flip; the heading now matches.
- The matching `super.manageUsers.colEnable` key is left untouched —
  the Users page still surfaces both directions of the toggle inline.

**Commit:** `a4a5e1e`

---

## 2026-05-31 — Load County / Zipcode pickers on demand for SUPER on Manage Polls

**Requested:**

> When I change the "State" filter from 'Any State' to for instance
> 'Alaska' the "County" filter doesn't populate with all the counties
> in Alaska, can we make it populate?

**Changed:**

- `/api/admin/polls/purview` returns an empty `counties` and
  `zipcodes` list for SUPER (the rationale was not to ship 3.3k
  counties / 30k+ zipcodes upfront). The frontend tried to fetch a
  `/api/zipcodes/all` URL that doesn't exist, so the lists never
  populated and a Picked state didn't surface its counties.
- New `refreshCountiesForState` calls `/api/counties?state_id=…` for
  SUPER when the State picker changes; restricted ADMINs keep
  filtering their assignment-derived list locally.
- New `refreshZipcodesForGeo` does the same for the Zipcode picker —
  uses `county_ids` when counties are picked, otherwise `state_id`.
- All state / county selection paths (click handler + Shift-\* /
  Shift-0 shortcut) now route through `afterStateChange` /
  `afterCountyChange` helpers that reset the down-cascade selections
  and trigger the appropriate API reloads.
- Dropped the misnamed `/api/zipcodes/all` call from `loadPurview`.

**Commit:** `c4e5b93`

---

## 2026-05-31 — Per-poll block scoping + flipped checkbox polarity

**Requested:**

> On the /admin/manage-polls page, when I check the Enabled/Disabled
> checkbox for the questionnaire titled 'Electric Cars' with no
> filters selected it enables ables not just 'Electric Cars' but the
> other Questionnaire titled 'Vaccines' too, how can we change that
> to just the poll selected?

> When I look at the questionnaire titled 'Electric Cars' and it has
> the Enabled/Disabled checkmark checked, does that mean it's enabled
> or disabled? … yes flip it

**Changed:**

- New Flyway migration **V15__poll_blocks_per_poll.sql** adds a
  `poll_id` column to `poll_type_blocks` and rebuilds the three
  partial unique indexes (`(poll_type, poll_id, zipcode|county_id|
  state_id)` per scope) plus a `(poll_type, poll_id)` lookup index.
  The earlier bucket model — block keyed only by `(poll_type, scope,
  geo)` — affected every poll of that type at that geo, so disabling
  Electric Cars at zip 98001 also disabled Vaccines. Per-poll scoping
  isolates the toggle to the row the admin clicked. Existing
  `poll_type_blocks` rows were dev test data and are dropped by the
  migration.
- `PollTypeBlock` entity carries `pollId`; repository finders now key
  on `(pollType, pollId)`; `existsByPollTypeAndPollId` powers the
  gate.
- `PollBlockService` simplifies to a one-row lookup; the bulk
  `filterUnblocked` keys by `(type, id)` and drops the zipcode-walk.
- The three response controllers, three results controllers, and
  `PollSearchController` all shed the zipcode-list arg in favor of
  `(pollType, pollId)`.
- `AdminPollsController.createBlock` stores `pollId` alongside the
  scope. `listBlocks` returns only blocks tied to this specific poll;
  `isBlockedFor` does a one-row `existsBy…` check.
- Frontend Enable/Disable checkbox polarity flipped: `:checked` now
  binds to `!row.blocked` so checked = enabled (matches
  `/super/manage-users`). The block modal still triggers on the
  disable transition (checked → unchecked); silent block removal on
  the enable transition (unchecked → checked).
- New test `block on one poll does not affect a sibling poll at the
  same zipcode` proves the isolation.
- Verified live: blocking Electric Cars at 98001 leaves
  `vaccines.blocked = false` in `/api/admin/polls`, and search /
  results behavior for unrelated polls is unchanged.

**Commit:** `47f388d`

---

## 2026-05-31 — Manage Polls — Show-disabled toggle, checkbox widget, Closes column

**Requested:**

> Can we add the 'Show disabled' checkbox to the Poll Type section that
> once clicked will show all disabled poll instances?

> Can we change the Enable/Disable widget from a button to a checkbox
> and if the checkbox goes from checked to unchecked/enabled then no
> modal box pops up to disable zipcodes, but if the checkbox goes from
> unchecked to checked then pop up the modal filter box?

> Can we add a "Closes" column that shows the polls closing date and
> is sortable by date?

**Changed:**

- Backend `GET /api/admin/polls` accepts an optional
  `includeDisabled=true` flag; when omitted, rows whose `blocked` is
  true are filtered out. Existing test was updated, and a new one
  covers both modes (default hides the blocked row; flag includes it).
- Frontend Poll-Type fieldset now contains a "Show disabled" checkbox
  bound to a `showDisabled` ref that drives the new flag.
- Enable/Disable cell switched from the colored Enabled/Disabled
  button to a plain checkbox bound to `row.blocked`:
  - Unchecked → checked: the existing block-modal opens so the admin
    picks ZIPCODE / COUNTY / STATE scope. The checkbox doesn't flip
    until the next refetch — `@click.prevent` keeps the visual state
    authoritative on the server.
  - Checked → unchecked: silently `DELETE`s every block currently
    affecting the poll (Promise.allSettled, per-block, so each
    purview-rejection stays isolated), then refetches. Blocks the
    admin can't remove (out of purview) leave the row checked.
- New "Closes" column sits between Zipcode and Enable/Disable. Cell
  formats `closeDate` via `toLocaleString()` (or `closeNever` when
  null). Sort key uses the raw ISO timestamp; null close-dates sort
  last in ascending order — treated as "never closes".
- i18n keys added: `admin.managePolls.showDisabled`, `colCloses`,
  `closeNever`.

**Commit:** `852e603`

---

## 2026-05-31 — /admin/manage-polls with submission blocking + notes

**Requested:**

> Can we update /admin/manage-polls so that it's roughly equivalent to
> /super/manage-users except: change the 'Role' section by replacing
> user, creator, admin checkboxes with election, questionaire and
> ballot measure checkboxes; change 'Email contains' with 'Title
> contains' and instead of 'Msg' use 'Notes' and let the Admin make
> notes in textareas. … Admins can disable/enable specific Poll types
> for a given zipcode, county or state within their purview. When a
> Poll type is disabled for a given zipcode then no more submissions
> are allowed for all instances of that poll type and zipcode(s),
> county or state.

> Also hide blocked polls from /polls/search and public results.

**Changed:**

- New Flyway migration **V14__admin_polls.sql** introducing two tables:
  - `poll_type_blocks (poll_type, scope ZIPCODE|COUNTY|STATE,
    zipcode|county_id|state_id, created_at, created_by)` with a CHECK
    enforcing exactly one geo identifier per row and three partial
    unique indexes (one per scope) so duplicate blocks at the same
    target are rejected.
  - `poll_notes (poll_type, poll_id, body ≤2000, author_id,
    timestamps)` — polymorphic on `(poll_type, poll_id)`. Notes are
    shared across admins; the latest preview appears in the table cell
    and full history is reachable via Prev/Next.
- New `PollKind` + `BlockScope` enums; `PollTypeBlock`, `PollNote`
  entities; `PollTypeBlockRepository`, `PollNoteRepository`.
- New `PollBlockService` answers "is this poll blocked?" by matching
  any of three scopes against a poll's zipcode set. Bulk path
  pre-resolves zip → (county, state) once per batch so search-page
  filtering stays O(N).
- New `AdminPollsController` at `/api/admin/polls`:
  - `GET /` — purview-scoped list with `pollType`, `title`, `stateId`,
    `countyId`, `zipcode`, `notesContain` filters. Each row exposes
    derived `blocked` (any active block matching the poll) and
    `latestNote`.
  - `GET /purview` — admin's available states/counties/zipcodes from
    enabled ADMIN-role assignments. SUPER returns `unrestricted=true`
    so the page works system-wide.
  - `GET/POST/DELETE /{type}/{id}/blocks` and `/blocks/{id}` — block
    CRUD; out-of-purview targets are rejected with 403.
  - `GET/POST /{type}/{id}/notes`, `PUT /notes/{id}` — note CRUD
    mirroring the /super/manage-users message flow (no
    Send-to-user).
- Enforcement wired into:
  - All three response controllers (`Questionnaire`, `Election`,
    `BallotMeasure`) — 403 with "Submissions disabled by admin" when a
    block matches, before any DB writes.
  - All three results controllers — 404 (treat as "not found") when a
    block matches.
  - `PollSearchController` — `PollBlockService.filterUnblocked`
    drops blocked rows before sort.
- New `frontend/src/views/admin/ManagePollsView.vue` (replaces the old
  stub): filter row with Poll-Type checkboxes (Election/Questionnaire/
  Ballot Measure), Title-contains with `/api/polls/search/suggestions`
  autocomplete, purview-gated State/County/Zipcode multi-select
  pickers, and a Notes free-text filter. Results table with sortable
  Title/Type/State/County/Zipcode/Enable-Disable/Notes columns. The
  Enable/Disable cell is a button that opens a modal listing every
  active block on the poll with per-block Remove buttons and a "Add a
  new block" form with ZIPCODE/COUNTY/STATE radio scope. The Notes
  cell opens the same Prev/Next history modal as the Messages flow on
  /super/manage-users, minus the email checkbox.
- `admin.managePolls.*` i18n block populated.
- Backend test (`AdminPollsControllerTest`) covers purview gating,
  out-of-purview block 403, block + note CRUD.
- Verified end-to-end against the running backend: SUPER purview is
  unrestricted; blocking an ELECTION by ZIPCODE makes
  `/api/polls/search` drop the row, returns 404 from the results
  endpoint, and 403 from the responses endpoint. Removing the block
  restores all three.

**Commit:** `13e5452`

---

## 2026-05-28 — Stack Role checkboxes under the Role heading

**Requested:**

> Can we put the "User","Creator","Admin" and "Show disabled"
> checkboxes and headings directly under the "Role" heading in the
> filters section?

**Changed:**

- Reverted the inline `<legend class="contents">` layout: the Role
  legend is back on its own line, with the four checkboxes
  (User / Creator / Admin / Show-disabled) wrapping on the row
  immediately below it.
- The fieldset is still `sm:col-span-2` so all four checkboxes fit
  on a single row at typical widths.

**Commit:** `40e279b`

---

## 2026-05-28 — Align Manage Users filter row, match results-table font size

**Requested:**

> Could you make the left side of the form filters, namely Role,
> Zipcode and Messages so that the Role heading is at the same level
> vertically as the "Email contains" heading?

> Can we pull the "Role" heading and checkboxes up so that "Role"
> aligns vertically with "Show disabled"?

> Can we move the "user", "creator" and "Show disabled" checkboxes
> and labels to be on the same line as the "Admin" checkbox?

> Can we make the font size of the [filter] headings the same size as
> the results headings […]?

**Changed:**

- Form grid: `items-end` → `items-start`, so every filter cell
  top-aligns its heading regardless of how tall the body is.
- Role fieldset is now a single horizontal flex row: the `<legend>`
  uses `display: contents` so "Role" renders inline alongside the
  three role checkboxes instead of stacking above them.
- "Show disabled" was merged into the Role fieldset, and the
  fieldset spans two grid columns (`sm:col-span-2`). Role +
  User/Creator/Admin + Show-disabled all share one line at typical
  widths.
- Every filter cell root moved from `text-xs` to `text-sm` so the
  filter headings match the results-table headers exactly. The
  Role-row labels inherit, so User/Creator/Admin/Show-disabled also
  bump up.

**Commit:** `afc4a20`

---

## 2026-05-28 — Messages filter + sortable Zipcode on Manage Users

**Requested:**

> Can we make Zipcode sortable now?

> Can we add one more filter after the Zipcode filter named "Messages"
> so that it searches thru all the messages for a particular word or
> phrase?

**Changed:**

- Zipcode header is now click-sortable with the same toggle and ▲ / ▼
  indicator as the other columns. Sort value is the raw zipcode
  string.
- New "Messages" text input sits after the Zipcode picker. Typing fires
  a debounced (200ms) refetch that includes a `?message=…` param.
- Backend `SuperUsersController.list` accepts an optional `message`
  param and intersects the user pool with the set of user_ids whose
  body contains the substring (case-insensitive). Blank / whitespace-
  only values are treated as no filter.
- `UserMessageRepository.findUserIdsWithBodyContaining` runs the JPQL
  `LOWER(m.body) LIKE LOWER(CONCAT('%', :needle, '%'))` and returns
  distinct user ids.
- i18n: `super.manageUsers.messageFilter / messagePlaceholder /
  messageHelp` added.
- Test: new `SuperUsersControllerTest` case covers a positive match,
  a non-match, and the blank-string passthrough.
- Verified against the live backend: case-insensitive single word,
  multi-word substring (URL-encoded spaces), no-match, and the
  combined `role=USER&message=…` form.

**Commit:** `e5792d3`

---

## 2026-05-28 — Sortable columns on Manage Users

**Requested:**

> Would it be possible to have the results columns of "Email", "Role",
> "State", "County", "Enable/Disable" and "Msg" sortable alphabetically
> where "Enable/Disable" treats a "true" as alphabetically after
> "false"?

**Changed:**

- The six listed column headers on `/super/manage-users` are now
  clickable: click sorts that column ascending, click again to flip
  to descending. The active column shows ▲ / ▼.
- Sort runs client-side over the loaded result set via a new
  `sortedResults` computed; the backend's default email order is now
  just the initial state.
- Comparison is lowercase alphabetic on the raw field, with two
  domain-specific keys:
  - `isEnabled` compares the literal strings `'false'` and `'true'`
    — so ascending lists disabled rows first and enabled last, per the
    request.
  - `msg` compares `latestMessage.body ?? ''` — rows with no message
    sort first in ascending.
- Empty State / County values sort as `''`.
- Zipcode header stays non-sortable; the column wasn't in the request.
- Shift-click range on the Enable/Disable checkbox now slices over
  `sortedResults`, so a range follows what the user sees on screen.
  The anchor resets whenever the sort order changes.

**Commit:** `8a58eef`

---

## 2026-05-28 — Replace /super/manage-admins with /super/manage-users

**Requested:**

> Can we replace /super/manage-admins with a mockup I have that behaves
> just like /polls/search and use the route /super/manage-users?

(Followed by a wireframe in `Assets/Mockups/Super-Manage-Users.png` and
several follow-ups: the seventh column is **Msg**, not a date; the
latest message shows as a clickable preview that opens a popup with
Prev/Next over the user's history; "New msg?" composes a new one with
an optional "Send to user?" email; edits never re-email; empty cell
shows "(none) + New msg?"; row checkbox toggles `User.isEnabled`;
Demote-to-Creator stays inline on Admin rows; filters live-update with
no Search button; Email-contains autocompletes; Shift-\* / Shift-0 on
the Enable column.)

**Changed:**

- **Backend** — new `SuperUsersController` at `/api/super/users`:
  - `GET /` with `role`, `includeDisabled`, `email`, `stateId`,
    `countyId`, `zipcode` filters. Cascade follows the same precedence
    as `/polls/search` (zipcodes → counties → states → none). State
    and County are derived per-row from the user's zipcode via
    `CountyZips`. Latest message is batch-loaded and attached.
  - `GET /emails?prefix=…` — capped-at-20 autocomplete source for
    the Email-contains filter.
  - `POST /{id}/toggle-enabled` and `POST /bulk-toggle-enabled` —
    powers the row checkbox plus Shift-\* / Shift-0 / shift-range.
    Both filter to USER/CREATOR/ADMIN; SUPER never gets flipped.
  - `POST /{id}/demote` — preserves the old controller's capability,
    now scoped to Admins surfaced by this page.
  - `GET /{id}/messages`, `POST /{id}/messages` (with optional
    `sendEmail`), `PUT /messages/{id}` — power the Msg modal. Body is
    1–2000 chars enforced at controller + DB. Editing never re-emails.
- New Flyway migration **V13\_\_user\_messages.sql** with `user_id`,
  `author_id`, `body` (CHECK length 1..2000), `emailed`, timestamps,
  and a `(user_id, created_at DESC)` index for fast latest-per-user.
- New `UserMessage` entity + `UserMessageRepository`.
- Deleted **`SuperAdminController`** and its test — the per-zipcode
  `RoleAssignment.enabled` toggle it carried has no surface in the new
  page, and the user-account flag has replaced it.
- **Frontend** — new `views/super/ManageUsersView.vue`:
  - Role chips (User/Creator/Admin), Show-disabled, Email-contains
    with `<datalist>` autocomplete, and the same State/County/Zip
    multi-select dropdown pickers (with shift-range and Shift-\* /
    Shift-0) used on `/polls/search`. No Search button — every change
    triggers a debounced (150ms) refetch.
  - Results table: Email, Role, State, County, Zipcode,
    Enable/Disable (checkbox with shift-range bulk-toggle and header
    Shift-\* / Shift-0), Msg.
  - Msg cell: clicking the latest-message preview opens a modal with a
    2000-char textarea, Prev/Next over the user's history, a
    `New msg?` action that switches to a fresh-message form with a
    `Send to user?` checkbox (only on new messages).
  - Demote button inline on Admin rows.
- Route swap: removed `/super/manage-admins`, added
  `/super/manage-users` (`ManageUsers`). Dashboard link updated.
  Deleted `ManageAdminsView.vue`.
- i18n: `super.manageUsers.*` block added; `super.manageAdmins.*`
  removed; `super.dashboard.linksManageUsersTitle/Desc` replaced the
  old `…Admins…` keys. Added `common.save` / `common.saving`.
- Tests: new `SuperUsersControllerTest` covering role filter, disabled
  visibility, email-contains, zipcode-cascade geo derivation, toggle
  (and SUPER guard), bulk toggle (SUPER untouched), demote happy/error
  paths, create+edit messages (incl. email recording), 400 on empty /
  oversize body, and latestMessage attachment. `SuperEndpointsSecurityTest`
  updated to probe `/api/super/users` instead of `/admins`.
- Verified end-to-end against the running backend: list with each
  filter family, autocomplete, single + bulk toggle, create with
  `sendEmail` confirmed in Mailpit, edit, prev/next history, demote.

**Commit:** `634b4e4`

---

## 2026-05-26 — Enable County always, typeahead with autocomplete when no state

**Requested:**

> Can we make the "County" selectList enabled and it's only populated
> if a State has been selected, if no state is selected then a user
> can type the county name with autocomplete and a multi-selectList?

**Changed:**

- County trigger is no longer disabled when no state is picked.
  Opening it now always renders a small search input above the
  checkbox list. Behavior:
  - **State(s) selected**: input filters the pre-loaded county list
    locally; the placeholder reads "Filter…".
  - **No state**: input fires a debounced (200ms) prefix lookup
    against `/api/counties?prefix=…`; matches populate the
    checkboxes. Placeholder reads "Type a county name…".
- Multi-select, shift-click range, `*` / `Shift+0` shortcuts work in
  both modes and operate on the visible (filtered) list.
- Backend: `GeographyController.listCounties` accepts an optional
  `prefix` param (case-insensitive, capped at 50). When set,
  `state_id` is ignored. `state_id` itself became optional too. New
  repository method
  `CountyRepository.findByNameStartingWithIgnoreCaseOrderByName`
  carries the lookup.
- Verified: `?prefix=Whatcom` → 1 match; `?prefix=king` → 10
  (case-insensitive across states); `?prefix=zzzz` → 0;
  `?state_id=48` still returns 39 WA counties; bare `/api/counties`
  returns `[]` instead of erroring.

**Commit:** `4d8229d`

---

## 2026-05-26 — Multi-select County, matching the State + Zipcodes UX

**Requested:**

> County please

(Same multi-select treatment as State.)

**Changed:**

- **County** picker now matches State and Zipcodes: checkbox list,
  shift-click range from the last single click, `*` to select all,
  `Shift+0` to clear, hover-tooltip help via the new
  `search.filters.countyHelp` key. Trigger summary shows the single
  county name, "N counties selected", or "Any county".
- `selectedCountyId: number | ''` became `selectedCountyIds:
  number[]` throughout the component; cascade reload of zips now
  takes the union of all ticked counties.
- `PollSearchController.search` `countyId` param became a list — Spring
  binds the comma-separated value to `List<Long>`; the geo-filter
  pulls every county's zips into the acceptable set.
- Verified by replay: single `countyId=3006` still returns the WA
  polls (backwards compat); `countyId=3006,3000` returns the same
  set (both counties are covered); `countyId=2970,2971` returns just
  Electric Cars (the only poll covering Adams zips); a non-existent
  county returns 0.

**Commit:** `8eca6f0`

---

## 2026-05-26 — Multi-select State with the Zipcodes-style checkbox + shortcut UX

**Requested:**

> Can we make the State selectList a multi-selectList just like the
> Zipcodes multi-selectList with the same shortcuts?

**Changed:**

- **State** picker now matches the Zipcodes picker exactly: checkbox
  list, shift-click range from the last single click, `*` to select
  all, `Shift+0` to clear. Trigger summary shows the single state
  name, "N states selected" for multi-pick, or "Any state" when
  empty.
- New `search.filters.stateHelp` tooltip mirrors the Zipcodes one.
- `selectedStateId: number | ''` became `selectedStateIds: number[]`
  throughout the component; cascade and search-param logic updated.
- Backend cascade across multiple states:
  - `GET /api/counties?state_id=A,B` now accepts a list; returns the
    union of counties.
  - `GET /api/zipcodes?state_id=A,B` likewise returns the union of
    zips across the listed states.
  - `GET /api/polls/search?stateId=A,B` accepts a list; the geo-zip
    fallback resolves to the union of all those states' zips when
    no zipcode/county is set.
- New repository method `CountyRepository.findByStateIdIn` carries
  the multi-state lookup.
- County stays single-select — the user can drill into one county
  picked from the union of all selected states' counties.

**Commit:** `be95ac1`

---

## 2026-05-26 — Clear typeahead on state change + match State/County to Zipcodes dropdown style

**Requested:**

> It looks like when a user selects a state from the State selectList
> the county selectList is correctly populated but the Zipcodes
> selectList stays unpopulated. It'd be great if all selectLists
> could be like the populated Zipcodes multi selectList and the
> Zipcodes selectList could be populated by either a State being
> selected and/or a county being selected.

(Clarified: same trigger style, still single-select.)

**Changed:**

- Fix: `onStateChange` now resets `zipFilter`. Previously, if the
  user had typed e.g. "982" in the Any-State typeahead mode and then
  picked Arizona, the leftover prefix kept filtering the state-wide
  zipcode list to zero matches — the dropdown looked empty even
  though `zipcodeOptions` was populated. Clearing the filter on
  state change makes the dropdown reflect the chosen state's full
  zip list immediately.
- Refactor: replaced the native `<select>` for **State** and
  **County** with the same trigger-button + scrollable-dropdown
  pattern that the Zipcodes picker uses. Still single-select; click
  the trigger to expand, click an option to pick + close. The
  County trigger stays disabled until a state is picked. `onDocClick`
  and `onEsc` extended to cover both new pickers.

**Commit:** `4ad4453`

---

## 2026-05-26 — Hover-tooltip help on the Zipcodes label

**Requested:**

> Can we add a help alert that when one hovers over the Zipcodes
> label the help text appears "Shift-* to select All..Shift-0 to
> de-select All, click on one zip and shift click 5 zips down to
> select a range"?

**Changed:**

- Wrapped the "Zipcodes" heading text in a `<span>` with a `title`
  attribute and `cursor-help` styling. Hovering surfaces the
  keyboard-shortcut and shift-click docs as a native browser
  tooltip — supplementing the inline `zipcodeShiftHint` that only
  shows while the dropdown is open.
- New i18n key `search.filters.zipcodeHelp` carries the help text
  verbatim from the request.

**Commit:** `88b84b6`

---

## 2026-05-26 — Switch zipcode picker UI by State selection

**Requested:**

> Can we change it so that the if the State selectList is set to 'Any
> State' the auto-complete textbox with the placeholder text of
> "(e.g. 982..)" render but if the State selectList is set to
> 'Arizona' for instance then the pre-populated selectList is
> rendered?

**Changed:**

- The zipcode picker now renders in one of two mutually exclusive
  modes based on the State selection:
  - **State = Any:** typeahead text input with a native `<datalist>`
    of prefix matches. The user types digits, picks one suggestion,
    and that single zipcode is the geo filter. Optimized for "I know
    the exact zip".
  - **State = specific:** typeahead disappears; the pre-populated
    dropdown trigger renders instead. Click to expand, check
    multiple zips. Optimized for browsing.
- `search()` routes the geo query param accordingly: typed value in
  typeahead mode; `selectedZipcodes` / `countyId` / `stateId` in
  dropdown mode.

**Commit:** `78d8198`

---

## 2026-05-26 — Collapse the zipcode list into a click-to-open dropdown

**Requested:**

> Can we make it so when a user selects a value in a selectList like
> State for instance, which then populates the set of zipcodes to
> display in Zipcodes, can the Zipcodes selectList show the first
> zipcode and a down arrow then when the user clicks on the Zipcode
> selectList the list expands to a scrollable list with the first 5
> items showing and the rest strollable?

**Changed:**

- The zipcode picker now collapses to a compact trigger by default:
  shows the first selected zip (or the first available zip if none
  selected) + a chevron. Clicking the trigger absolute-positions the
  scrollable list below it; `max-h-32` (128px ≈ 5 rows at ~24px
  each) keeps the first ~5 items visible with the rest scrollable.
- Click outside or press Esc closes the dropdown. Extended the
  existing `onDocClick` + `onEsc` handlers (already in place for
  the zip-popover in results) to cover the new `data-zip-picker`.
- The chevron rotates 180° while open as an open/closed cue.
- When more than one zip is selected, the trigger reads
  "N zipcodes selected" instead of a single zipcode (new
  `search.filters.zipcodeNSelected` i18n key).
- The shift-click hint only surfaces while the picker is open — no
  point cluttering the closed state with shortcut docs.

**Commit:** `2fe44ae`

---

## 2026-05-26 — Move the zipcode picker back under its own column header

**Requested:**

> Can we have the selection of a state populate the zipcodes widget
> underneath the "Zipcodes" heading and remove the selectList on the
> bottom row?

**Changed:**

- Reverted commit `fc3a963`'s "list below the grid" split. The
  zipcode picker — empty-state hints, the scrollable checkbox list,
  and the shift-click hint — is back under the "Zipcodes" heading in
  the same grid cell as the typeahead.
- The cell is now taller than the others; `items-end` on the grid
  still bottom-anchors the shorter cells against it (the zipcode
  header sits at the top of its own column, the other headers sit
  lower).

**Commit:** `ceafb7d`

---

## 2026-05-26 — Hint the prefix-search nature of the zipcode placeholder

**Requested:**

> Can we change the placeholder text for the Zipcodes selectList
> from "(e.g. 982)" to "(e.g. 982..)"

**Changed:**

- Updated `search.filters.zipcodeTypeahead` to `"(e.g. 982..)"`. The
  trailing `..` cues the user that the input takes a prefix rather
  than the full 5-digit zipcode.

**Commit:** `cb859bb`

---

## 2026-05-26 — Shorten the zipcode placeholder so the close paren fits

**Requested:**

> Can we change the zipcodes placeholder text from "(e.g. 982" to
> "(e.g. 982)"?

**Changed:**

- The original `zipcodeTypeahead` string `"Type digits to filter
  (e.g. 982)"` was wider than the input at the form's narrowest grid
  breakpoint, so the rendered placeholder was getting truncated
  mid-paren. Shortened to `"(e.g. 982)"` so the entire example fits.

**Commit:** `e429eaf`

---

## 2026-05-26 — Label state filter "State (populates zipcodes)"

**Requested:**

> Can we change the state selectList heading from "State" to "State
> (populates zipcodes)"?

**Changed:**

- Updated `search.filters.state` in `en.json`. The cascade behavior
  (picking a state surfaces the zipcode checkbox list below) wasn't
  obvious from the bare "State" label.

**Commit:** `095c047`

---

## 2026-05-26 — Align the zipcode header with the rest of the filter labels

**Requested:**

> Can we move the zipcodes selectList down so the "zipcodes" header
> aligns vertically with the rest of the inputs?

**Changed:**

- Split the zipcode picker into two pieces:
  - The grid `<label>` now carries only the header + typeahead input,
    making the cell the same height as the other filter cells. With
    `items-end` on the grid, headers and inputs all line up.
  - The scrollable checkbox list, empty-state hints, and the
    shift-click hint moved into a row beneath the grid inside the
    same `<form>`. The picker still reacts to the typeahead and
    state/county selections; only its DOM location changed.

**Commit:** `fc3a963`

---

## 2026-05-26 — Tighten the zipcode start-hint wording

**Requested:**

> Can we change the placeholder text for the zipcodes box from "Pick
> a state above, or start typing a zipcode here." to "Pick a state or
> start typing a zipcode here."?

**Changed:**

- Updated `search.filters.zipcodeStartHint` in `en.json`. Drops the
  comma and the "above" reference since the input is right next to
  the hint.

**Commit:** `f01b629`

---

## 2026-05-26 — Zipcode typeahead — type digits without picking a state

**Requested:**

> Can we adjust the zipcode field so that you don't have to select
> the state first, if you know the exact zipcode then just start
> typing into the zipcode box then matches will be shown as the user
> types the digits?

**Changed:**

- Added a typeahead input above the zipcode checkbox list on
  `PollSearchView`. When no state is selected, typing digits fires a
  debounced (200ms) prefix lookup against `/api/zipcodes?prefix=…`
  and the matching zips appear as checkboxes. When a state is
  selected, typing narrows the already-loaded state/county list
  locally without a network call.
- `/api/zipcodes` accepts a new `prefix` param. Results are capped at
  50 so the dropdown stays scrollable — refine by typing more
  digits. `CountyZipsRepository.findByZipcodeStartingWithOrderByZipcode`
  added.
- `*` and `Shift+0` shortcuts now operate on `displayedZipcodes` (the
  filtered subset) instead of `zipcodeOptions`, so they respect the
  typeahead. Shift-click range works on the filtered indices too.
- Replaced the "Pick a state first" placeholder with a "Pick a state
  above, or start typing a zipcode here" hint that points at the new
  input.
- Verified: `?prefix=982` returns the 50 zips starting with 982;
  `?prefix=98264` returns the single exact match; `?prefix=00000`
  returns 0; prefix takes precedence over `state_id` when both are
  sent.

**Commit:** `5543189`

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
