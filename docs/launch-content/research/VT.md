# Vermont: 2026 general election research

Researched 2026-10-23 for the November 3, 2026 election.

## Sources

**Official (primary)**

- SOS *2026 General Election Qualified Candidates* spreadsheet, linked from
  https://sos.vermont.gov/elections/election-info-resources/candidates/ and parsed with Python's stdlib.
- SOS election-info page for poll hours, and the election-law page for the majority requirement.
- VSARA list of constitutional proposals, giving the legislative history of PR.3 and PR.4.
- Legislature texts of Proposal 3 and Proposal 4, "As adopted by the Senate Official" (2026 folder), giving the
  amendment text and Sec. 3 effective date. The PDFs were decoded with Python's zlib.

**Secondary (cross-check only)**

- Ballotpedia: *Vermont elections, 2026* and the Proposal 3 and Proposal 4 pages, which carry the ballot-title
  wording.

## Findings

- There is no U.S. Senate race this year.
- Statewide (6 contests, each elected separately):
  - Governor: 5 candidates. Janoo is the Democratic/Progressive fusion nominee, against Scott (R), Roy (Freedom
    and Unity), Goodband (Peace and Justice) and Judd (Independent).
  - Lieutenant Governor: 2 candidates.
  - Treasurer: 3 candidates.
  - Secretary of State: 3 candidates.
  - Auditor of Accounts: 2 candidates.
  - Attorney General: 2 candidates.
- House at-large: Balint (D), Malloy (R) and four independents.
- Governor, Lieutenant Governor and Treasurer need a majority to win. Otherwise the legislature picks one of the
  top three in January.
- Measures (2 legislatively referred amendments):
  - Proposal 3: a right to collective bargaining.
  - Proposal 4: equal protection.
- Effective date: each proposal takes effect on Nov 3, 2026 "when ratified".
- Polls close at 7 p.m. EST, which is 2026-11-04T00:00Z.

## Disagreements

- The PR.3 PDF also contains the 2023 Senate draft, which adds a union-dues clause. The final text matches
  Ballotpedia and has no dues clause, so the final text is used.
- Both proposals are numbered "Article 23", as written.

## Gaps

- The ballot question wording ("To see if the voters will amend…") comes from Ballotpedia. No SOS sample
  ballot was found.
- The SOS list gives names in all capitals. Normal case is used here, and party labels were not checked
  against the printed ballot.
