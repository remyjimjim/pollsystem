# Releases

Each release is a git tag (`release-X.Y`). Its message is the short version
of the notes below. `docs/DEVLOG.md` has the change-by-change detail.

## 1.0 — the election-week release

Tagged after the prod backend deploy with V44 and a phone walkthrough on
prod. BallotBox launched publicly on Mon 2026-10-26 (from 0.81); 1.0 is
what people use on election day, Tue Nov 3.

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

### Search and results

- A place (state, county or zipcode) finds the polls inside it. A county
  works without a state.
- Back from a poll returns to the same search results.
- Search loads several times faster (it was 6–52 s on prod).
- "Purview" replaces "Zipcodes", in search, on the Create dashboard and on
  results.
- Result bars have thicker, easier-to-read text.
- Reviewing a submitted ballot highlights each pick.

### Phones

- Cards instead of tables on the Create dashboard, Manage Polls, Manage
  Creators, Manage Users, History, the Admin dashboard and Creator
  requests.
- ↓ / ↑ arrows to jump to the end of long lists and back to the top.

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
- Dark theme refinements.
- App version 1.0.0 (frontend `package.json`, backend `build.gradle.kts`;
  the Dockerfile's jar name follows).

### Before tagging

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
