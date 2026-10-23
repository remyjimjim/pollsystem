# North Dakota: 2026 general election research

Researched 2026-10-23 for the November 3, 2026 election.

## Sources

**Official (primary)**

- Secretary of State, *2026 General Election Contest/Candidate List* (statewide jurisdiction):
  https://vip.sos.nd.gov/candidatelist.aspx?eid=348. This is an ASP.NET form, so it was queried by posting the
  form with Python's stdlib.
- SOS *Measures on the Ballot* page, plus the official ballot-language PDFs, analyses, voter guide and the
  Measure 2 full text. The PDFs were decoded locally with Python's zlib.
- N.D.C.C. 16.1-01-03 (poll hours), and N.D. Const. art. III secs. 8-9 and art. IV sec. 16 (effective dates).

**Secondary (cross-check only)**

- Ballotpedia, *North Dakota elections, 2026*. Its candidate list matches the SOS list.

## Findings

- No U.S. Senate or Governor race this year.
- Statewide (9 contests): Secretary of State, Attorney General, Agriculture Commissioner, Tax Commissioner, and
  Public Service Commissioner for both the full term and an unexpired 2-year term (all R vs Democratic-NPL).
  There are also three nonpartisan contests:
  - Superintendent of Public Instruction (unexpired 2-year term): Bachmeier vs Foss.
  - Supreme Court full term: Tufte vs Johnson.
  - Supreme Court unexpired 8-year term: Bahr, unopposed.
- House at-large: Fedorchak (R), Hammer (D-NPL), and two independents (Neville, Tuttle).
- Measures (2):
  - Constitutional Measure No. 1 (HCR 3003) would require 60% to approve constitutional amendments.
  - Initiated Constitutional Measure No. 2 would require free school breakfast and lunch.
- Effective date: initiated measures take effect 30 days after the election, which is 2026-12-03. A
  legislative amendment takes effect on approval. 2026-12-03 is used as the representative date.
- Poll close: by law polls close between 7 and 9 p.m. local time. The latest possible close is 9 p.m. MST in
  the southwest, which is 2026-11-04T04:00Z.

## Disagreements

- Ballotpedia gives the AG nominee as "Timothy Lamb", while the SOS lists "Tim Lamb". The SOS name is used.
- Measure 1 wording: the voter guide drops "proposing a constitutional amendment" after "legislative assembly".
  The official ballot-language PDF wording is used.

## Gaps

- The 9 p.m. close is the statutory maximum. Actual county closing hours were not checked.
- Ballot-face party labels ("independent nomination" in the SOS list, shown as "Independent") were not checked
  against a sample ballot.
- The June primary results were not reviewed separately. The general-election candidate list governs.
