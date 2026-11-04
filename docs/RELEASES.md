# Releases

Each release is a git tag (`release-X.Y`). Its message is the short version
of the notes below. `docs/DEVLOG.md` has the change-by-change detail.

## Since 1.0 (not yet tagged)

- **District maps for every state** (`/maps/XX`), linked from each state's
  midterm results pages:
  - 34 states drawn from the Census lines;
  - the 6 at-large states as one district (Alaska's ranked race counts 1st
    choices);
  - numbered boxes for the 10 states that redrew for 2026, until their
    official maps are added.

## 1.0 — the election-week release

**Tagged 2026-11-04,** after the prod backend deploy (Fly v33: V44 ranked
choice, V45 moderation mode) and a phone walkthrough on prod. BallotBox
launched publicly on Mon 2026-10-26 (from 0.81).

### New since 0.81

- **Ranked-choice races (Alaska).**
  - Rank candidates 1st, 2nd, 3rd… from a dropdown; the ballot refuses gaps.
  - Results show the instant-runoff count round by round: who was
    eliminated, the winner, and the ballots with no choices left.
  - Ties go to earlier rounds, then alphabetical order. Maine stays
    single-choice.
- **View past ballots:** a list of the polls you've answered, each
  reviewable read-only, with Closed and Enabled? columns.
- **Substack membership:** a paid norfstar Substack subscription includes
  BallotBox membership, through the Substack Stripe account's webhook and a
  re-sync.
- **Sign in with a 6-digit code** from the email, which works on phones
  where the link opens in another browser. There's a "Signing in" help
  topic.
- **Demo account:** a shared demo address a super switches on for 12 hours.
- **Navigation by role:** Vote, Create, and Admin ▾ / Super ▾ menus.
- **District maps for Washington, Oregon and Idaho** (`/maps/WA`, `/maps/OR`,
  `/maps/ID`),
  linked from each state's midterm results pages:
  - Dem / Rep / Ind / Other / top 2 (the two leading parties), with
    colours you pick;
  - a district picker that zooms in;
  - a ballot measure split by district;
  - the results' privacy rules.
  Other states follow with `scripts/maps/build-state-map.py`.
- **Listed words checked everywhere people type:** titles, summaries,
  questions, office names, reasons, notes and messages, not just comments.
  Candidates' names are exempt. Private comments follow the setting too.
  A super switches Block / Warn on the Super dashboard, in force at once
  (`docs/MODERATION.md`).
- **Results from the first vote while getting started:** the results
  minimum is 1 until 100 people have voted, then 4 for good (narrowed groups
  always 4). The Privacy Policy says so (effective November 4, 2026).

### Search and results

- A place (state, county or zipcode) finds the polls inside it. A county
  works without a state.
- Back from a poll returns to the same search results.
- Search loads several times faster (it was 6–52 s on prod).
- "Purview" replaces "Zipcodes", in search, on the Create dashboard and on
  results.
- Result bars have thicker, easier-to-read text.
- Public comments on ranked-choice races sit under their candidate.
- Reviewing a submitted ballot highlights each pick.

### Phones

- Cards instead of tables on the Create dashboard, Manage Polls, Manage
  Creators, Manage Users, History, the Admin dashboard and Creator
  requests.
- ↓ / ↑ arrows to jump to the end of long lists and back to the top, on
  every list that can run past the screen (ballots, results, admin and
  super pages, creator forms and reports).

### Admin and Super

- Manage Polls:
  - a Show nationwide option;
  - editable title and summary;
  - an election's close date carries to its measures;
  - a notes list.
- The old Super Manage Polls page is retired.
- History has a Diff column (Before / Now side by side).
- The Create dashboard loads in bulk (19 s → fast for 624 polls).

### Other

- Membership is $4.99 a month, live and in the sandbox.
- A "Why?" page in the footer; a Terms line about Substack.
- Moderation catches plurals and more slurs.
- Dark theme refinements; card and panel outlines are the text colour.
- The home page's search card reads "Find polls and Vote".
- App version 1.0.0 (frontend `package.json`, backend `build.gradle.kts`;
  the Dockerfile's jar name follows).

### Before tagging (done)

1. The user tries ranked choice on staging from a phone.
2. The user runs the prod backend deploy (V44 runs; the Alaska races become
   ranked).
3. A phone walkthrough on prod: sign in, vote, results, past ballots.
4. `git tag -a release-1.0 -m "…"` on the deployed commit, then push the
   tag.

## Earlier

| Tag | Date | What |
|---|---|---|
| `release-0.81` | 2026-10-25 | Stacked search-result cards on phones; English-only banner on translated policies |
| `release-0.8` | 2026-10-25 | Settings gear: Dark / Light themes, colors, text size; results threshold 4 |
| `release-0.5` | 2026-10-24 | All 50 states' 2026 midterm ballots (one per district); midterm voting rules |
| `release-0` | 2026-10-15 | Known-good fallback point |
