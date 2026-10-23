# Wyoming: 2026 general election research

Researched 2026-10-23 for the November 3, 2026 election.

## Sources

**Official (primary)**

- SOS *2026 Election Information* page and its *General Election Candidates* data file (CSV), which has a
  Ballot Name column and withdrawal dates.
- SOS *2026 Statewide Ballot Propositions* PDF ("ballot language … as it will appear"), the *2026 Voter
  Guide* and *Judges Standing for Retention*. The PDFs were decoded with Python's zlib.
- SOS election FAQ for poll hours (7 a.m. to 7 p.m.), and W.S. 22-13-117 and 22-16-118 (Title 22 PDF).
- Wyoming Constitution art. 3, sec. 52(f) (initiative passage and effective date).

**Secondary (cross-check only)**

- Ballotpedia, *Wyoming elections, 2026*. It agrees on all nominees and the single measure.

## Findings

- Statewide (6 contests):
  - U.S. Senate: Hageman (R) vs Byrd (D).
  - Governor: Barlow (R), Casner (D) and Bextel (Constitution). Wyoming has no Lieutenant Governor.
  - Secretary of State: R vs D vs Constitution.
  - State Auditor (Racines) and State Treasurer (Meier): both Republicans, unopposed.
  - Superintendent of Public Instruction: R vs D.
- House at-large (open seat): Gray (R), Kinney (D), Johnson (Libertarian) and Haggit (Constitution).
- Measures (1): Proposed Initiative Proposition Number One, a 50% homeowner property tax exemption (initiated
  statute). No legislative constitutional amendments are on the 2026 ballot.
- Passage needs a majority of all voters in the general election, not just a majority of those voting on the
  question.
- Effective date: 90 days after certification. The canvass deadline is Nov 11, so the representative date is
  2027-02-09.
- Polls close at 7 p.m. MST statewide, which is 2026-11-04T02:00Z.
- Supreme Court retention for Hill and Jarosh is on the ballot but is not modeled.

## Disagreements

- Ballotpedia shortens two names ("Ken Casner", "Jeff Haggit"). The SOS ballot names are used.

## Gaps

- Party wording on the ballot face was not checked against a sample ballot. Sample ballots are published by
  counties.
- The certification date is a statutory deadline, not a confirmed date.
- The fiscal-impact lines are kept in the ballot language because the SOS document places them before
  FOR/AGAINST. This was not checked against a printed ballot.
