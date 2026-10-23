# Nebraska: 2026 general election research

Researched 2026-10-23.

## Sources

**Official**
- Nebraska SOS *Final Statewide General Candidate List* (PDF, dated 9/11/2026). It was downloaded with curl and its text extracted using Python's standard library (zlib plus string parsing). All candidates and parties come from it.
- Nebraska SOS *2026 Ballot Measures Pamphlet* (PDF, decoded the same way). The ballot questions for Initiatives 440, 441 and 442 come from it.
- LR19CA slip law (nebraskalegislature.gov). Its Sec. 2 sets the ballot language verbatim.
- SOS *2026 Election Calendar*: polls close 8 PM CT / 7 PM MT, and the Board of State Canvassers meets Nov 30.
- Neb. Const. Art. III sec. 4 and Art. XVI sec. 1, for the effective-date rules.

**Secondary:** none. The Ballotpedia page came back empty, and web search was unavailable because the session's search budget was used up.

## Results
- **6 statewide races, 19 candidates:** U.S. Senate (5), Gov / Lt Gov (4 tickets), Secretary of State (4), Treasurer (2), Attorney General (2), Auditor of Public Accounts (2).
  - There is no Democratic Senate candidate. Dan Osborn runs "By Petition".
  - Party labels include Legal Marijuana NOW, Nebraska Working People and America First.
- **3 House districts, 12 candidates.** NE-2 is an open seat (no incumbent listed).
- **4 measures:**
  - Initiative 440: online sports wagering, constitutional amendment.
  - Initiative 441: online sports wagering, statute; operative Jan 1, 2027.
  - Initiative 442: school athletic teams by biological sex, constitutional amendment.
  - LR19CA: legislative term limits, from 2 to 3 consecutive terms.
- **Polls:** the latest close is 02:00 UTC on Nov 4. That is 8 PM CST in Central counties and 7 PM MST in Mountain counties, the same instant.
- **Effective date:** initiatives take effect on the Governor's proclamation, which must come within 10 days after the canvass. LR19CA "shall become a part of this Constitution" on adoption, with no separate date. The representative date is 2026-11-30, the State Canvassers' certification date.
- **Not included:** the nonpartisan Legislature, Public Service Commission (district races) and judicial retention are noted only.

## Gaps
- LR19CA's printed designation is unknown. The SOS "Approved Ballot Statement Language" PDF is a scanned image that could not be read without OCR.
- `ballotTitle` holds only the "Shall ..." question. The "A vote FOR / AGAINST" explanatory statements are left out.
- Changes after the 9/11 list, and party wording on the ballot face (e.g. "By Petition"), were not checked against a sample ballot.
