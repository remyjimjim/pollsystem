# Wisconsin: 2026 general election research

Researched 2026-10-23 for the November 3, 2026 election.

## Sources

**Official (primary)**

- La Crosse County official sample ballot, *Partisan Office and Referendum*, Nov 3, 2026 (94th Assembly District, CD-3):
  https://townoffarmingtonlax.org/assets/files/2026/08/hc2-samp.pdf
- Kenosha County Type C *Notice of Referendum Election* (Aug 26, 2026), which gives the official ballot questions and
  explanatory statements: https://wheatlandkenoshaco.gov/documents/763/TYPE_C_NOTICE_OF_REFERENDUM_2026.docx
- WEC *Candidates on Ballot by Election*, 2026 General Election, printed June 9, 2026 (a WEC report reposted by WRN):
  https://cdn.wrn.com/wp-content/uploads/2026/06/Candidates-on-Ballot-By-Election_Post-June-9-2026-Ballot-Access-Meeting.pdf
- Wis. Stat. 7.70(3)(a) and (h): https://docs.legis.wisconsin.gov/document/statutes/7.70(3)

elections.wi.gov and myvote.wi.gov returned HTTP 403 (Cloudflare). The PDFs were decoded locally with Python's stdlib.

**Secondary (cross-check only)**

- Ballotpedia and Wikipedia (WI U.S. House 2026): primary winners and general-election fields.

## Findings

- No U.S. Senate race. Statewide: Gov / Lt Gov (2 tickets), Attorney General (2), Secretary of State (3, including
  Wisconsin Green), Treasurer (2). All confirmed on the sample ballot.
- U.S. House: 8 districts, 21 candidates. CD-2 has only Mark Pocan. CD-3 has 4 candidates (2 independents), CD-4
  has 3, and CD-6 has 5 (Green plus 2 independents).
- Measures: three legislatively referred constitutional amendments, Questions 1–3 (partial veto on taxes and fees,
  places of worship during emergencies, and a ban on government discrimination or preferences).
- Effective date: on WEC certification of the canvass, due on or before Dec 1. 2026-12-01 is used as the
  representative date.
- Polls close at 8:00 PM CST, which is 2026-11-04T02:00:00Z. The whole state is on Central time.

## Disagreements

- Name forms: Ballotpedia has "Bradley Smith" and "Elizabeth Fitzgibbon", while WEC has "Brad Smith" and
  "Elizabeth Anne Fitzgibbon". The WEC forms are used.
- BallotReady labels Pete Karas "(I)", but both WEC and the sample ballot say "Wisconsin Green".
- Ballotpedia lists independent governor candidates; none are on the WEC list or the ballot.
- The Kenosha explanatory statement for Question 3 cites "art. I, § 18" in its yes/no lines, which is a typo for
  § 27.

## Gaps

- Ballot names for every district except CD-3 rest on the June 9 WEC list, not on a printed general ballot.
- Primary winners for CD-1, 4, 6, 7 and 8 were checked only against secondary sources.
- One word of the Question 2 title did not decode from the ballot PDF, so that word relies on the Kenosha notice.
