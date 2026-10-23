# Minnesota: November 3, 2026 general election research

Researched 2026-10-23.

## Sources

**Official (primary basis):**
- Minnesota Secretary of State (SOS) election-results feed for 11/03/2026
  (`electionresultsfiles.sos.mn.gov/20261103/` with `ussenate`, `ushouse`, `governor`,
  `attorneygen`, `secofstate`, `auditor`, `judicial` `.txt`). The SOS preloads this feed
  with the general-election ballot. Its vote counts are pre-election test data and were
  ignored. Only the names and party codes were used.
- SOS official Aug 11, 2026 primary results (`/20260811/`). Every major-party nominee in
  the general feed won their primary.
- Laws 2026, chapter 114 (HF 3900) on revisor.mn.gov. This is the source of the amendment
  text, the ballot question, the ballot title and the effective date.

**Secondary (cross-check only):**
- A Hennepin County SD45/HD45A sample ballot (PDF, decoded with Python's stdlib). It gives
  the printed party spellings. It matched the SOS feed for every statewide race, every
  statewide judicial seat, CD3 and the amendment wording.
- Naomi Kritzer's CD5 post and The Ballot Brief, both checked for CD5.

The SOS candidate-filing site (candidates.sos.mn.gov) and the sos.mn.gov "What's on my
ballot" pages returned a Radware captcha and could not be read. Ballotpedia returned an
empty page.

## Findings

- 5 statewide executive races: U.S. Senate (regular seat, no special), Gov / Lt Gov,
  Attorney General, Secretary of State and State Auditor.
- 7 statewide judicial seats: Supreme Court Associate Justice 1 and 4, and Court of
  Appeals 1, 7, 13, 15 and 18. Each has only the incumbent printed. These races are
  nonpartisan.
- 8 House districts with 17 printed candidates.
- 1 constitutional amendment, "Increasing Funding to School Districts" (permanent school
  fund). It is unnumbered on the ballot. Its own text says it takes effect July 1, 2027.
- Polls close at 8:00 PM Central (CST), which is 02:00 UTC on Nov 4.

## Disagreements

- **CD5, DeVelle L. Jackson:** the SOS code is IND, and the same code prints as
  "Independent" for Senate candidate Simonetti. The Kritzer post also says "Independent".
  The Ballot Brief says "Independence-Alliance Party". "Independent" is used.
- **CD5 Republican nominee:** an old Ballotpedia snapshot names Dalia Al-Aqidi. The
  official primary results show John Nagel won with 52.16%, so Nagel is used.

## Gaps

- Printed party labels for CD1, CD2 and CD4 to CD8 come from the SOS codes plus the
  Hennepin spellings. No printed ballot from those districts was seen.
- No direct check of the SOS candidate list for late withdrawals, because of the captcha.
