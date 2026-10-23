# Indiana: Nov 3, 2026 general election research

Researched 2026-10-23. The data is in `IN.json`: 3 statewide offices, 9 House districts and 2 public questions.

## Sources

**Official (primary):**
- The Indiana Election Division's "Candidate List - Abbreviated", 2026 General Election, all counties, run 2026-09-22. It is an .xlsx file linked from https://www.in.gov/sos/elections/candidate-information/ and was parsed with the Python standard library. Every candidate and party comes from it, using its `BALLOT NAME` column.
- Two official county sample ballots on in.gov: Noble County precinct P7 (2026-09-05) and Switzerland County (2026-09-30). These supplied the public question language, ballot order, printed party labels and retention questions. Both were decoded with Python's zlib.

**Secondary (cross-checks):**
- The Indiana Capital Chronicle voter guide and its February 2026 article on SJR 1.
- Fox19 (Oct 7, 2026) on both public questions.
- WNDU and the Capital Chronicle on the Senate's rejection of redistricting.
- UW–Madison State Democracy Research Initiative (Art. 16 §1(c)).

Ballotpedia returned empty pages (not used).

## Agreement

- The official list matches both sample ballots and the White County list.
- The only disagreement found is that ivoterguide (as of Aug 1) leaves out Greg Ballard (Lincoln Party, Secretary of State). He filed 2026-07-14 and is on the official list and both ballots.
- No U.S. Senate seat is up.
- Redistricting: the Senate voted 31-19 against a new map on 2025-12-11, so the 2022 map remains in effect.

## Gaps and caveats

- **Public question spacing:** PDF extraction lost spaces at line breaks. The text was rebuilt by comparing the two ballots, which break lines in different places. Wording matches in both, but some line breaks (for example before "(1)" and "(2)") may be printed as new lines rather than spaces.
- **Effective date:** Under Art. 16 §1(c), an amendment becomes part of the Constitution once a majority of those voting on it ratify it. 2026-11-03 is used as the date. The certification date was not found.
- **PQ2's joint resolution** was not identified.
- **Judicial retention** (statewide): Court of Appeals Judges DeBoer and Felix, and Tax Court Judge McAdam. Noted, not modeled.
- **Party labels:** the official list's labels are used, with the minor party as "Lincoln Party". Ballot wording varies by county.
- **Late changes:** nothing after 2026-09-30 was checked.
- **Poll close:** 6:00 PM CST in the Central-time counties, which is 2026-11-04T00:00:00Z.
