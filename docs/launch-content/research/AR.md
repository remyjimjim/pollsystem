# Arkansas: Nov 3, 2026 general election

Researched 2026-10-23.

## Sources

- **Candidates:** the Secretary of State's official election-night reporting
  site (Tally ENR, linked from sos.arkansas.gov). The "2026 General Election &
  Judical Runoff" contest setup (v87, updated Oct 21) lists every contest and
  ballot name. Those names carry REP/DEM/LIB prefixes; full party names come
  from the same feed's party table. I cross-checked against Green Papers,
  Ballotpedia and the Libertarian Party of Arkansas nominee list (Talk
  Business, Feb 2026), and all of them agree.
- **Measures:** the SOS Initiatives and Referenda page and its four "Notice to
  the Public" PDFs. I decoded the PDFs with Python's standard library. Their
  text matches Ballotpedia and the UA Division of Agriculture voter guide.

## Contents

- **Statewide offices (8):** U.S. Senate (3 candidates), Governor (3), Lt.
  Governor (2), Secretary of State (3; Kim Hammer won the GOP runoff), Attorney
  General (1), Treasurer (1), Auditor (1) and Land Commissioner (2). The three
  one-candidate races are Republican-only but still appear as contests on the
  official feed.
- **U.S. House (4 districts):** CD1 and CD3 each have 3 candidates; CD2 and CD4
  each have 2. No independents qualified.
- **Measures:** 4 legislative referrals.
  - Issue 1: citizens-only voting (HJR 1018)
  - Issue 2: right to keep and bear arms (SJR 11)
  - Issue 3: economic development districts (SJR 15)
  - Issue 4: $500M water and pollution-abatement bonds (Act 578 of 2025)

  There are no citizen initiatives.
- **Not included:** the feed has no statewide judicial runoff; its judicial
  contests are local.

## Effective date

Issues 1–3 each say "effective on and after January 1, 2027". Issue 4 is a bond
authorization with no date in its notice. **2027-01-01** is used as the
representative date.

## Poll close

All of Arkansas is on Central time. Polls close at 7:30 PM CST, which is
**2026-11-04T01:30:00Z**.

## Confidence and gaps

Confidence is high: both candidates and measures come from SOS-hosted data.
Gaps:

- The names come from the results feed, not a printed county sample ballot, so
  small differences on the printed ballot face are possible.
- Poll hours were confirmed only through secondary sources citing Ark. Code
  7-5-304.
- I did not read the full text of Act 578, the act behind Issue 4.

The web-search budget ran out partway through, so the later checks used
direct page fetches only.
