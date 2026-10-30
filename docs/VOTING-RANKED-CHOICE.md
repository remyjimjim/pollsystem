# Ranked-choice voting — plan

Status: **decided 2026-10-30, being built** (phases 1–2 before Nov 3).

**Decisions (2026-10-30):**

1. **Build now**, before Nov 3: phases 1–2 first.
2. **Maine stays single-choice** (2 candidates per race).
3. **Ties:** earlier rounds, then alphabetical.
4. **Rank picker:** a dropdown per candidate.
5. **Results:** totals per round only (no "moved votes" detail).
6. **`allowRankedChoice`** on by default.

## Why

On Nov 3, 2026 two states count some races by ranked choice (instant runoff):

| State | Ranked races (real ballot) | In BallotBox today |
|---|---|---|
| **Alaska** | Every general-election race: U.S. Senate, U.S. House, Governor / Lt. Governor, state legislature. The ranked count is published **Nov 18**, after late overseas ballots. | "2026 Midterm Alaska": U.S. Senate, U.S. Representative At-Large, Gov / Lt Gov, **4 candidates each** |
| **Maine** | Federal races only (U.S. Senate, U.S. House), and only when **3 or more** candidates run, write-ins included. **Not** the governor (Maine's constitution requires a plurality for state offices). | U.S. Senator, U.S. Representative Districts 1 and 2, **2 candidates each** |

Alaska's **Ballot Measure No. 2** (already on BallotBox) would repeal ranked
choice for future elections; this election still uses it.

Today each of these races is single-choice, labelled "(ranked-choice on the
ballot; pick your first choice here)". So a voter can't record their real
ranking, and the results can't show who would win the ranked count.

Sources: FairVote, *Where will ranked choice voting be used in the 2026
elections?*; NCSL, *Ranked Choice Voting*; The Maine Monitor, *How
ranked-choice voting will work in Maine's 2026 election*.

### What actually needs ranking in our data

- **Alaska: 3 races × 4 candidates.** Ranking matters here.
- **Maine: 2 candidates per race.** With two candidates, the first choice
  wins outright; there's nothing to transfer. Keep them single-choice, but
  the design lets any race be switched to ranked if a third candidate is
  added.
- **Votes so far** on these races (2026-10-29): **0** on prod and on local.
  So there's nothing to migrate.

## What the voter sees

### Voting

A ranked race shows its candidates with a **rank picker** beside each one: a
small dropdown with `—`, `1st`, `2nd`, `3rd`, `4th`. It works with a
keyboard, a screen reader and on phones; drag-and-drop doesn't do all three.

- Picking a rank already used **moves** that rank. For example, picking
  "1st" for B when A was 1st makes A unranked. No two candidates ever share
  a rank.
- You may rank **as many or as few** as you like, but at least **1st** to
  count. Ranks must run 1, 2, 3 with no gaps: picking "3rd" with no "2nd"
  shows "Pick your 2nd choice first".
- A one-line explanation sits under the race heading: "Rank the candidates
  in order of preference. If no one has a majority of first choices, the
  last-place candidate is eliminated and their voters' next choices count
  instead."
- Validation messages follow the existing pattern ("Rank at least your 1st
  choice for <race>", linked to the race).

### Reviewing ("View past ballots" / "No, just review")

The race lists your ranking in order, "1st: A, 2nd: B, 3rd: C", with the
"✓ Your vote" badge on your 1st choice. Unranked candidates are faded.

### Results

For a ranked race, the results show **rounds**:

```
Round 1        A 41%   B 33%   C 18%   D 8%  → D eliminated
Round 2        A 43%   B 36%   C 21%         → C eliminated (its votes moved: 12% to B, 6% to A, 3% exhausted)
Round 3 (final) A 49%   B 51%                → B wins
```

- Each round is a row of the existing light-blue bars. The last round is
  marked **Winner**.
- **"Exhausted"** ballots (no remaining choice) are shown per round, so the
  percentages add up.
- A collapsed **"How this was counted"** explains instant runoff in two
  sentences.
- The current single-choice view, "1st choices only", remains as round 1.

## Counting rules (instant runoff)

Per race, over the ballots in the current results filter (inside or
outside the purview, and the state/county/zipcode filters):

1. Each ballot counts for its **highest-ranked continuing** candidate.
2. If a candidate has **more than half** of the continuing ballots
   (exhausted ones don't count), they win.
3. Otherwise **eliminate the candidate with the fewest votes**. Their
   ballots move to each ballot's next continuing choice. Repeat.
4. With **2 candidates left**, the higher one wins. This matches Alaska and
   Maine, which stop at the final two.
5. **Ties for last place:** eliminate the one with fewer votes in the
   previous round, then the round before, and so on. If still tied,
   eliminate the one **later in alphabetical order**. That's deterministic
   and documented. Real elections draw lots; a simulation shouldn't be
   random.
6. A ballot with **no ranks** in the race doesn't count in it, as today
   with no pick.

This is pure computation on (ballot → ordered candidate ids). It belongs in
one Kotlin function (`InstantRunoff.count(ballots, candidates)`), tested on
its own.

## Privacy (the results threshold)

Today results stay hidden until **k = 4** respondents (Dev override: 1), and
every group shown must have at least 4 (the group minimum). Non-supers see
stepped, rounded percentages.

- **The whole race** stays hidden until k ballots rank at least a 1st choice.
- **Each round:** percentages only, rounded the same way. Never raw counts
  for non-supers, and **no "moved votes" breakdown** that would reveal a
  group smaller than 4. If a transfer group is under 4, show only the new
  totals.
- **Supers** see raw counts per round, as today.

## Data model (migration V44)

Offices are shared across elections ("U.S. Senator" is one row used by
every state), so "ranked" belongs to the **race in an election**, not the
office:

```sql
-- V44__ranked_choice.sql
CREATE TABLE ranked_races (
    election_id BIGINT NOT NULL REFERENCES elections(id) ON DELETE CASCADE,
    office_id   BIGINT NOT NULL REFERENCES offices(id),
    PRIMARY KEY (election_id, office_id)
);
ALTER TABLE candidate_responses ADD COLUMN rank SMALLINT;   -- 1 = first choice; NULL = single-choice race
```

- **A ranked vote:** one `candidate_responses` row per ranked candidate,
  `response = true`, `rank = 1..n`. Unranked candidates have no row.
- **Single-choice races** are unchanged (`rank` is NULL), so existing votes,
  results and exports keep working.
- The election DTO gains `rankedOfficeIds: number[]`. The creator's election
  form gains a **"Ranked choice"** checkbox per race, saved to
  `ranked_races`.
- **Data:** a small script marks the 3 Alaska races ranked. It also drops
  the "(ranked-choice on the ballot; pick your first choice here)" suffix
  from those offices, and keeps a shorter "(ranked choice)" note on the
  Maine ones. The suffix is in shared office names, so this is done per
  election, in the same pass.

## Templates: no new poll type, one switch on the Election template

**Considered:** a new poll type, "Ranked-choice election", with its own
template, or a ranked picker set in the Election template's `widget`.
**Rejected**, because:

1. **Ranked choice is per race, not per ballot.** Maine's statewide ballot
   has a single-choice **Governor** next to a ranked **U.S. Senator**. A
   district ballot *includes* its statewide election, so one ballot page
   mixes both kinds. A template applies to every poll of its type, so it
   can't say "this race ranked, that one not". `ranked_races` can.
2. **A new poll type has side effects.** Creator access is granted **per
   poll type**: every creator would need a new grant to make ranked
   elections. Search's **Type** filter, "Election" labels and counts, and
   code that recognizes elections by type would all have to learn the new
   type. Alaska's ballot would stop being an "Election" in search.
3. **Everything else is the same:** candidates, races, purview, measures,
   results, review. Only the picker and the counting differ.

**Instead:** keep the one Election poll type, mark ranked races per race
(`ranked_races`), and add one optional switch to the Election template,
next to the existing picker setting:

```json
"candidates": {
  "widget": "selectOneRadio",
  "groupBy": "officeName",
  "allowRankedChoice": true,
  ...
}
```

| `allowRankedChoice` | Effect |
|---|---|
| `true` | The election form shows a **"Ranked choice"** checkbox per race. Checked races use the rank picker and instant-runoff results; the others use `widget` as today. |
| `false` or missing | No checkbox. Races already marked ranked **stay ranked**: voters and results don't change. Creators just can't mark new ones. |

- **Set by a super** in Super → Poll Templates, like the rest of the
  template. The default in the seeded Election template is `true`.
- `widget` keeps choosing the **single-choice** picker (radio, checkbox,
  dropdown). Ranked races always use the rank picker, whatever `widget`
  says.
- **Server side:** saving an election with a ranked race when the template
  says `false` is refused (400 "Ranked choice isn't enabled for this poll
  type"), unless the race was already ranked.
- **Phase 3** (creator side) adds this: a few lines in
  `ElectionService.readCandidatesHints`, the DTO, and the form. It also
  goes in `docs/manual/super/poll-templates.md`.

## API changes

| Endpoint | Change |
|---|---|
| `GET /api/polls/elections/{id}` | adds `rankedOfficeIds` |
| `POST /api/polls/elections/{id}/responses` | ranked races send `{candidateId, rank}`. The server checks ranks are 1..n with no gaps or duplicates and at least 1st, and that the candidates belong to that race. Otherwise 400 with the race name. |
| `GET …/responses/me` | each response carries `rank`; the review and edit modes use it |
| `GET /api/polls/elections/{id}/results` | ranked races add `rounds: [{ shares: {candidateId: pct}, exhaustedPct, eliminated: candidateId?, winner: candidateId? }]` (raw counts for supers) |
| Cancel my votes, account deletion, demo end, snapshots, creator report | delete and recount as today. `rank` rides along with the rows. The result snapshot stores the rounds. |

## Work, in phases

| Phase | What | Estimate |
|---|---|---|
| **1. Vote and review** | V44; DTO and validation; rank-picker widget; edit and review modes; mark the Alaska races; tests | ~1 day |
| **2. Results** | `InstantRunoff` with tests (majority in round 1, several rounds, exhausted ballots, every tie rule); results DTO; rounds UI; privacy rules; snapshots | ~1–1.5 days |
| **3. Creator side** | "Ranked choice" per race in the election form, shown when the template's `allowRankedChoice` is on; help pages (Answering polls, Viewing results, Creating elections, Poll templates); 9 languages | ~½ day |

About **2.5–3 days** in all. Phases 1 and 2 could land before Nov 3 if
started right away. Otherwise, Alaska's own ranked count is on **Nov 18**,
so BallotBox's ranked results stay timely well after election day.

### Interim option (minutes, no new features)

Make the existing note clearer on the 3 Alaska races: "Alaska counts this
race by ranked choice. Here, pick your **1st** choice." Results then show
1st choices, which is round 1 of the real count.

## Decisions needed

1. **Build it now (before Nov 3) or after?** And just phase 1 first?
2. **Maine:** keep single-choice (2 candidates, so ranking can't change the
   outcome), or mark ranked anyway for consistency with the real ballot?
3. **Tie rule:** OK with "previous rounds, then alphabetical", rather than
   random?
4. **Rank picker:** dropdown per candidate (recommended), or drag-and-drop
   ordering (nicer on desktop, harder on phones and for accessibility)?
5. **"Moved votes" detail** in results: show it when every group is 4 or
   more, or keep results to totals per round only?
6. **Template switch default:** `allowRankedChoice: true` on the Election
   template (recommended), or off until a super turns it on?
