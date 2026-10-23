# North Carolina: 2026 general election research

Researched 2026-10-23 for the November 3, 2026 election.

## Sources

**Official (primary)**

- NCSBE, *Candidate List Grouped by Contest* for the 11/03/2026 election, run Oct 13, 2026:
  https://s3.amazonaws.com/dl.ncsbe.gov/Elections/2026/Candidate%20Filing/2026_general_candidate_list_by_contest.pdf
- NCSBE, *Referendum Choices List* (statewide), dated Sep 2, 2026, with the official ballot question text:
  https://s3.amazonaws.com/dl.ncsbe.gov/Elections/2026/Candidate%20Filing/statewide_referendums_20261103.pdf
- NCSBE candidate-lists index: https://www.ncsbe.gov/results-data/candidate-lists
- NCSBE Election Day hours (6:30 a.m. to 7:30 p.m.): https://www.ncsbe.gov/voting/vote-person-election-day
- Enabling acts: S.L. 2026-4 (income tax cap) and S.L. 2026-5 (property tax levy limit) on ncleg.gov, plus the
  legislative summary of S.L. 2024-58 (photo ID).

WebFetch could not read either NCSBE PDF. The text was pulled locally from the saved PDFs by decompressing their
streams with Python's stdlib. The referendum PDF needed a glyph-ID decode, and its output read back cleanly.

**Secondary (cross-check only)**

- Protect Democracy 2026 NC Certification Guide, for the state canvass date (Nov 24, 2026).
- WUNC/NC Newsline (Aug 10, 2026), for NC-11. Rep. Chuck Edwards withdrew and Jennifer Balkcom was named the
  replacement nominee, which matches the NCSBE list.

## Findings

- Statewide: U.S. Senate (4 candidates), NC Supreme Court Associate Justice Seat 01, and NC Court of Appeals
  Seats 01–03. NC judicial races are partisan.
- U.S. House: all 14 districts are contested (2–3 candidates each).
- Measures: three legislatively referred constitutional amendments (photo ID for all voting, a 3.5% income tax
  cap, and a property tax levy limit). NC does not number them, so the NCSBE contest titles are used as
  designations.
- Effective date: on certification of the results. 2026-11-24 (the statutory state canvass) is used as the
  representative date. The income tax cap applies from tax year 2027.
- Polls close at 7:30 PM EST, which is 2026-11-04T00:30:00Z. The whole state is on Eastern time.

## Disagreements

- None between official and secondary sources.
- One ABC11 explainer gives a Nov 26 certification date, but that date belongs to the 2024 cycle.

## Gaps

- The NCSBE list gives party abbreviations (DEM/REP/LIB/GRE). The full party names used here were not checked
  against a printed sample ballot.
- Candidate changes after the Oct 13 list run were not checked beyond news. Ballotpedia's NC House page was
  unreadable.
- Primary results were not reviewed separately; the general-election list governs the ballot.
- The certificate date may move if the canvass recesses (to as late as Dec 4) or a protest is pending.
