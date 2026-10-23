# Kentucky (KY): 2026 general election research

Researched 2026-10-23.

## Sources

**Official (primary):**
- Kentucky Secretary of State, Candidate Filings database, 2026 General Election
  (web.sos.ky.gov/CandidateFilings): U.S. Senator (id=3), U.S. Representative
  (id=4), Supreme Court (id=14), Court of Appeals (id=15), Constitutional
  Amendment 1 (id=23). This is HTML, so I read it directly.
- SOS "2026 Constitutional Amendments" page: the official ballot question and
  the amended text of Section 77.
- SB 10 (2026 RS), the enrolled bill PDF, decoded with Python's standard
  library. Its question text matches the SOS page word for word.
- State Board of Elections 2026 Election Calendar (PDF, decoded the same way):
  polls are open 6 a.m. to 6 p.m. prevailing time, and the State Board
  tabulates by Nov 23 (KRS 118.425(5)).

**Secondary (cross-check only):** LPM (Senate primary: Booker, Barr),
Spectrum News and others (Gallrein beat Massie in KY-4), and WUKY/Kentucky
Lantern (Alvarado and Dembo in KY-6). All of them agree with the SOS list. The
Kentucky Lantern voter guide returned HTTP 403. The search snippets showed it
listing Christopher Campbell (Kentucky Party) for Senate, which matches the SOS.

## Findings

- **U.S. Senate (open seat, McConnell retiring):** Charles Booker (D), Andy
  Barr (R), Christopher Campbell (Kentucky Party).
- **House:** 6 districts and 17 printed candidates. Minor-party and independent
  candidates are on the ballot in KY-2, KY-4, KY-5 and KY-6.
- **No statewide judicial races.** Kentucky elects its appellate judges by
  district, without party labels.
- **One measure:** Constitutional Amendment 1, which limits pardons and
  commutations around gubernatorial elections.
- **Poll close:** 6:00 PM Central = 2026-11-04T00:00Z.

## Disagreements

- An earlier NKy Tribune article quoted a 30-day window for the amendment. That
  came from a prior version of the bill. The enacted SB 10 and the SOS page say
  60 days.
- The question says the ban runs until the Governor "is sworn in". The amended
  text says it runs until "the fifth Tuesday succeeding the election", which is
  Kentucky's inauguration day. My summary follows the constitutional text.
- SB 10 originally also amended Section 240. A House floor amendment removed
  that section, so the measure now changes Section 77 only.

## Gaps

- I did not check a county sample ballot, so these are unverified: the exact
  party wording printed, the candidate order, and whether the ballot prints the
  label "Constitutional Amendment 1".
- No source states the amendment's effective date explicitly. 2026-11-23 is the
  statutory deadline for the State Board's tabulation.
- I left out the write-ins and Mohammad Wael Ahmad (KY-4, Kentucky Party). The
  SOS lists Ahmad as withdrawn, deceased or disqualified.
