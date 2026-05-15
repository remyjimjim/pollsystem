# Development Log

A running record of changes, pairing the request that prompted each one
(in the requester's own words) with a summary of what was done. Newest
entries are added at the top.

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
