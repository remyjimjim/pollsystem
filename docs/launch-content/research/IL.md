# Illinois: 2026 general election research

Researched 2026-10-23 for the November 3, 2026 election.

## Sources

**Official (primary)**

- Illinois State Board of Elections (SBE), Candidate Filing Search for "GENERAL ELECTION - 11/3/2026"
  (US Senator, Statewide Executive Offices, Representative in Congress; Active and All statuses):
  https://www.elections.il.gov/ElectionOperations/CandidateFilingSearch.aspx?ID=sejIrI%2bQmww%3d
  WebFetch got a 403, so the ASP.NET form was posted with Python's stdlib and the result tables were parsed locally.
- Poll hours, 10 ILCS 5/17-1 (6:00 a.m. to 7:00 p.m.), and Ill. Const. art. XIV, sec. 2 (amendment effect), on ilga.gov.

**Secondary (cross-check only)**

- Ballotpedia: the Senate, Governor and U.S. House 2026 pages, plus *Illinois 2026 ballot measures*
  ("zero statewide ballot measures" certified as of Oct 20, 2026).
- CPA Practice Advisor/Capitol News (Apr 24, 2026): the millionaire-tax amendment missed the May 3 deadline.

## Findings

- Statewide offices: U.S. Senate (open seat; 3 candidates), the Gov / Lt Gov ticket (3), and Attorney General,
  Secretary of State, Comptroller and Treasurer (2 each).
- U.S. House: all 17 districts are contested. IL-4 has 4 candidates and every other district has 2, for 36 in total.
- Measures: none statewide. The redistricting amendment passed only the House. Local and advisory county questions
  (for example Cook County's millionaire-tax advisory question) are out of scope.
- Effective-date rule: an amendment takes effect "as the amendment provides" once approved. The election date is used
  as a placeholder.
- Polls close at 7:00 PM CST, which is 2026-11-04T01:00:00Z. The whole state is on Central time.

## Disagreements

- None on who is on the ballot. Ballotpedia matches the SBE Active list in every race checked. It lists further
  Governor tickets, but only as write-ins, and it marks the Green ticket as removed and running as a write-in.
- There are minor name-form differences. Ballotpedia has "Daniel K. Biss" and "Dillan Vancil" where the SBE has
  "Daniel Biss" and "Dillan S. Vancil". The SBE forms are used.

## Gaps

- Names and party labels were not checked against a printed sample ballot. The SBE prints parties in capitals and
  names as "Last, First"; here they are title-cased and put in First Last order.
- No official SBE page affirmatively lists "no statewide referenda". That conclusion rests on Ballotpedia and news
  reports.
- Primary results were not reviewed separately. The SBE general-election list, which carries the primary winners and
  the vacancy fillers such as Treasurer nominee Max Solomon, governs.
- Candidates the SBE removed, such as the Green Party slate and the IL-4 independents Macias and Sigcho Lopez, are
  listed in the JSON notes.
