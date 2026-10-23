# South Dakota: 2026 general election research

Researched 2026-10-23 for the November 3, 2026 election.

## Sources

**Official (primary)**

- Secretary of State VIP candidate list, 2026 general election: https://vip.sdsos.gov/candidatelist.aspx?eid=774.
  It gives party codes and a "Withdrawn" flag.
- SOS *2026 Statewide Ballot Questions* page and the *2026 Ballot Questions* pro/con pamphlet (PDF, decoded with
  Python's zlib). The pamphlet carries each Attorney General title, the explanation and the yes/no recitation. The
  separate AG statement PDFs are scanned images and could not be read.
- SDCL 12-2-3 (poll hours) and SDCL 2-1-12 (effective date of measures), via the LRC statute API.

**Secondary (cross-check only)**

- Ballotpedia, *South Dakota elections, 2026*. It agrees on all nominees and the four measures.

## Findings

- Statewide (8 contests):
  - U.S. Senate: Rounds (R) vs Bengs (Independent). The Democratic nominee Beaudion withdrew on Aug 4.
  - Gov / Lt Gov: Rhoden/Venhuizen (R) vs Ahlers/McCleerey (D).
  - Secretary of State: R, D and Libertarian candidates.
  - Attorney General: Russell (R) is unopposed.
  - State Auditor, State Treasurer, Commissioner of School and Public Lands, and Public Utilities Commissioner:
    R vs D in each.
- House at-large: Jackley (R) vs Gronli (D).
- Measures (4, all legislatively referred constitutional amendments):
  - I: end Medicaid expansion if the federal share drops below 90%.
  - J: citizens-only voting.
  - K: an unclaimed property trust fund.
  - L: a 60% vote for constitutional amendments.
- Effective date: July 1 after the state canvass, which is 2027-07-01.
- Polls close at 7 p.m. local time, and West River is on Mountain time, so the last close is
  2026-11-04T02:00Z.

## Disagreements

- The SOS page lists the legislature's resolution titles (e.g. "conditioning the requirement of expanded
  Medicaid…"). The pamphlet carries the AG ballot titles (e.g. "Repeals Expanded Medicaid Coverage if…"). The
  AG titles are what voters see, so those are used.

## Gaps

- Party wording on the ballot face was not checked against a sample ballot.
- Amendment I's AG explanation says "federal property level", apparently meaning "poverty". It is kept verbatim.
- Several circulating petitions (an SB 245 referendum, initiative/referendum amendments, a property-tax repeal)
  appear on the SOS page but not in the pamphlet or on Ballotpedia. They are treated as not qualified.
