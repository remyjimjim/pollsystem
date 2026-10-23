# Kansas: 2026 general election research

Researched 2026-10-23.

## Sources

**Official**
- Kansas SOS *Candidate Lists*, "2026 General" (https://www.sos.ks.gov/elections/elections_upcoming_candidate.aspx). This is an ASP.NET form. It was posted with Python's standard library and the HTML table parsed. All candidates and parties come from it.
- Kansas SOS *Proposed Constitutional Amendments* (HTML page; the PDF version was not needed). The ballot language comes from it.
- Kansas SOS *Important Election Dates*: the State Board of Canvassers must certify by Dec 1.
- K.S.A. 25-106 (poll hours), Kansas Constitution Art. 14 §1, and the HCR 5004 supplemental note (PDF, decoded with zlib).

**Secondary (cross-checks)**
- Kansas Reflector: redistricting ended (2026-04-22) and the Aug 4 judicial amendment was rejected. Lawrence Times / KCUR: primary results.
- A search snippet of Sherman County's election page, for its hours. The page returned 403 when fetched directly.

## Results
- **6 statewide races:** U.S. Senate (3 candidates), Gov / Lt Gov (2 tickets), Secretary of State (3, including United Kansas), Attorney General (2), State Treasurer (3), Commissioner of Insurance (3).
- **4 House districts** on the 2022 map, which stays in effect because the GOP's mid-decade redraw failed. Each district has a Democrat, a Republican and a Libertarian, for 12 candidates.
- **1 measure:** HCR 5004, a constitutional amendment on citizenship for voting.
- **Retention:** Supreme Court Justices Rosen and Walsh, plus 5 Court of Appeals judges. These are noted only.
- **Polls:** the latest close is 02:00 UTC on Nov 4. Mountain-time Sherman County closes at 7 PM MST. K.S.A. 25-106 also allows Central counties to close as late as 8 PM CST, which is the same instant.
- **Effective date:** an approved amendment "shall become a part of the constitution". The representative date is 2026-12-01, the canvass certification deadline.

## Gaps
- The ballot face was not checked against a sample ballot. Names follow the SOS table, which drops periods in some names (e.g. "David C Graham").
- `ballotTitle` holds the explanatory statement only. The full amended section, with its strikethrough, is left out for length.
- The supplemental note's wording differs slightly from the SOS page; the SOS wording is used.
- The Aug 4 amendment on Supreme Court elections (rejected) is not on the November ballot.
