# New Jersey: 2026 general election research

Researched 2026-10-23 for the November 3, 2026 election.

## Sources

**Official (primary)**

- NJ Division of Elections 2026 page: https://www.nj.gov/state/elections/election-information-2026.shtml
- The Division's *Official List* of U.S. Senate candidates (07/27/2026) and U.S. House candidates (09/04/2026), the
  July 27 *Certification of General Election Nominees*, and the amended NJ-7 certification (09/04/2026), all under
  `nj.gov/state/elections/assets/pdf/election-results/2026/`.
- Monmouth County Clerk: the official Federal & County candidate list and the Avon Borough sample ballot. These were
  used for printed letter case and to look for a public-question section.

WebFetch could not read the PDFs. The text was pulled from the saved files with a Python stdlib decoder that
inflates the streams and maps glyphs through each font's ToUnicode map.

**Secondary (cross-check only)**

- iVoterGuide's NJ 2026 guide, which matches the official list apart from NJ-7.
- New Jersey Globe, on the NJ-7 Libertarian's removal.
- Ballotpedia: it has no "New Jersey 2026 ballot measures" page, and its hours page gives 6 a.m. to 8 p.m.

## Findings

- Statewide: U.S. Senate only, with 4 candidates (Booker D, Murphy R, Fernandez "End the Corruption!", Kuniansky
  Socialist Workers Party).
- U.S. House: all 12 districts, 35 printed candidates (2–4 per district). NJ-8 has no Republican nominee.
- Measures: none. The Division's public-question links are commented-out placeholders that return 404, and the
  sample ballot has no question section.
- Effective-date rule (for reference): an approved amendment takes effect 30 days after the election (N.J. Const.
  Art. IX, para. 6). That would be 2026-12-03.
- Polls close at 8:00 PM EST, which is 2026-11-04T01:00:00Z. The whole state is on Eastern time.

## Disagreements

- **NJ-7:** iVoterGuide still lists Lana Leguía (Libertarian). She is on the July 27 certification, but an appellate
  ruling removed her and the NJ Supreme Court declined review. The amended certification and the Sept 4 list omit
  her, so she is excluded here.
- **Two independents:** Mora (NJ-2) and Eliopoulos (NJ-8) carry different slogans in different counties. They are
  recorded as "Independent".

## Gaps

- Only Monmouth's ballot was seen for letter case. Other slogans are title-cased from the all-capitals list.
- The "no statewide question" finding rests on missing evidence plus one sample ballot. No official page says
  "none".
- The outcome of Leguía's federal suit was not found.
- Primary results were not reviewed separately; the official general list governs the ballot.
