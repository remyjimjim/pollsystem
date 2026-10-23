# Georgia: Nov 3, 2026 ballot research

Researched 2026-10-23.

## Sources

**Official (county sample ballots built from Secretary of State ballot content):**
- White County consolidated sample ballot, which includes the SOS final certified write-in list (2026-09-15): https://www.whitecountyga.gov/DocumentCenter/View/3746/11032026-Consolidated-Sample-Ballot-Write-Ins
- Fulton: https://www.fultoncountyga.gov/-/media/2026-Election/November-3-Election/Sample-Ballot--Consolidated--Nov-32026-General--Special-Election--1082026.pdf (CD 5, 6, 7)
- DeKalb: https://dekalbcountyga.gov/sites/default/files/2026-09/sample-booklet.pdf (CD 4, 5, 13)
- Gwinnett: https://www.gwinnettcounty.com/documents/d/gwinnett-county/nov-3-general-election-special-election-consolidated-sample-ballot-english-spanish (CD 4, 9, 10, 13)
- Cobb, a draft v4: https://assets.cobbcounty.gov/files/2026-09/SAMPLE%20BALLOT%20DRAFT%20v4%20WEB.pdf (CD 6, 11, 14)
- Bibb and Houston (CD 2, 8); Columbia and Augusta-Richmond (CD 12)
- The SOS 2026 Constitutional Amendments booklet, read from Columbia County's copy at https://www.columbiacountyga.gov/DocumentCenter/View/10734 because sos.ga.gov returned 403

**Secondary (used to cross-check):** Ballotpedia race and measure pages, WTOC/Atlanta News First (June 17 nominee list), ballot-access.org (Libertarian access), votesee.org and WTXL (GA-1).

Every sos.ga.gov page and PDF was blocked by Cloudflare (403). The PDFs were text-extracted locally with pypdf.

## Disagreements

- **Libertarians:** Ballotpedia's Aug 10 news piece listed Thomas Blooming (L) for PSC 5. Ballotpedia's PSC race page now lists him as withdrawn or disqualified, and no sample ballot shows him. Ballot-access.org (July 10) reports no Libertarians on the statewide ballot. He is excluded.
- **Name forms:** Ballotpedia uses names like "James Kingston", "Clayton Fuller", "Sanford Bishop Jr." and "Pamela DeLancy". Where a sample ballot was available, the JSON uses the ballot's form instead (e.g. "Clay Fuller", "Sanford Bishop", "Pamela "Pam" Delancy").
- **Counties differ on styling:** Fulton prints "BONGO" and DeKalb prints "Bongo". Gwinnett omits the comma in "Johnson, Jr.".
- **Amendment order:** the AJC listed the 9-1-1 fund first. The official ballots number the conservation-use amendment 1, probate judges 2 and 9-1-1 fund 3.

## Gaps

- **GA-1:** no official ballot text was readable. "Jim Kingston" (R) vs "Amanda Hollowell" (D) comes from news and voter guides, so the exact name form is unconfirmed.
- **GA-3:** Jack (R) vs Keller (D) is confirmed only via a search-engine snapshot of an official Enhanced Voting sample ballot, plus Ballotpedia.
- **Measures:** there are three constitutional amendments and no statewide referendum. The effective date is Jan 1, 2027, the Georgia constitutional default; the resolutions set no other date.
- **Poll close:** 7:00 PM EST, which is 2026-11-04T00:00:00Z.
