# Hawaii: 2026 general election research

Researched 2026-10-23 for the November 3, 2026 election.

## Sources

**Official (primary)**

- Office of Elections 2026 Candidate Report (OLVR): https://olvr.hawaii.gov/Controls/CandidateFiling.aspx?elid=94
  The page shows 15 rows at a time, so the full list was pulled through its CSV export (file dated 2026-10-22).
  The candidates used are the ones with status "In General".
- 2026 General Election Voter Guide (English, 96 pages). The PDF text was decoded locally with Python's zlib.
  It lists the same general-election candidates, office by office, with their party letters.
- Proposed constitutional amendments page, which gives the official question text:
  https://elections.hawaii.gov/voting/2026-proposed-amendments-to-the-hawaii-state-constitution/
- Contest schedule (offices up in 2026) and the Hawaii Constitution, art. XVII, sec. 3 (LRB).

**Secondary (cross-check only)**

- Ballotpedia, which also lists two certified measures and notes the implementing bill for the RISE bonds.

## Findings

- No U.S. Senate seat is up this year.
- Statewide races:
  - Governor and Lieutenant Governor run as one ticket. Green / Kawakami (D) face Cordery / Anthony (R).
  - Office of Hawaiian Affairs trustee races are nonpartisan and on every ballot statewide:
    - At-Large: 6 candidates, vote for 3.
    - Maui Resident: 2 candidates.
    - Oahu Resident: 2 candidates.
- U.S. House:
  - HI-1 has 4 candidates: Case (D), Lam (R), Conley (Green) and Berning (nonpartisan).
  - HI-2 has 2: Tokuda (D) and Awa (R).
- Measures: Questions 1 and 2 are both legislatively referred constitutional amendments.
  - Question 1 gives the Senate 60 days instead of 30 to act on judicial appointments.
  - Question 2 covers county RISE bonds.
- Approval needs two things: a majority of the votes tallied on the question, and yes votes that are at least
  50% of all ballots cast. This means blank votes count against an amendment.
- Each amendment takes effect on ratification. The election date is used as the representative date.
- Ballots must reach the county by 7 PM HST, which is 2026-11-04T05:00Z.

## Gaps / unconfirmed

- The party wording on the ballot face and how the ticket is printed were not checked against a sample ballot.
- Some Hawaiian diacritics are dropped in the official CSV.
- capitol.hawaii.gov blocked requests. Because of that, the bills' effective-date sections and HRS ch. 13D
  (OHA electorate) could not be read.
- The date the results are certified was not confirmed.
