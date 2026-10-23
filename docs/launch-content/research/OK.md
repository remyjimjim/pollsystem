# Oklahoma: 2026 general election research

Researched 2026-10-23 for the November 3, 2026 election.

## Sources

**Official (primary)**

- State Election Board, *2026 Candidate List Book* (April 1-3 filing), with ballot names and parties.
- State Election Board pages listing withdrawals, contests of candidacy, the Next Election page (polls open 7 a.m. to 7 p.m.) and State Questions ("Two state questions have qualified").
- Secretary of State SQ packets 845.pdf and 847.pdf, which hold the AG's Final Ballot Titles. SQ 845 uses the corrected title filed Aug 13, 2026.

WebFetch could not read these PDFs. I pulled the text locally with Python's stdlib (zlib stream decode, cp1252).
The official candidate site (filings.okelections.gov) and the results site (results.okelections.us) both returned HTTP 403.

**Secondary (to identify primary and runoff winners)**

The Green Papers, Wikipedia (the Senate, House and Lt. Governor pages) and News9 (the governor runoff, and the Treasurer, Labor, Insurance and Corporation Commission races). Ballotpedia rendered empty.

## Findings

- **Statewide:** U.S. Senate (5 candidates: Hern R, Thomas D, White L and two independents), Governor (5), Lt. Governor (2), AG (2), Treasurer (2), Superintendent (2), Labor (3), Insurance (2) and Corporation Commissioner (2). The Auditor and Inspector race is empty: Melissa Capps (R) was unopposed and is deemed elected.
- **U.S. House:** all 5 districts are contested, with 2-4 candidates each. In CD1, Lahmeyer withdrew from the runoff, which made Tedford the Republican nominee.
- **Measures:** SQ 845 (Judicial Nominating Commission) and SQ 847 (property valuation caps and the senior freeze). Both are legislatively referred constitutional amendments.
- **Effective date:** an amendment takes effect on approval and certification. I used 2026-11-10 (the Tuesday after the election) as the representative date. SQ 847's caps apply from tax year 2027.
- **Polls close** at 7:00 PM CST, which is 2026-11-04T01:00:00Z.

## Gaps

- No official post-runoff ballot list was readable, so the nominees are cross-checked rather than taken from an official source. The Superintendent and AG nominees rest on secondary sources only.
- I did not check the party wording printed on the ballot face. The filing book says "Democrat".
- Judicial retention names were not researched.
- I did not read the enabling resolutions for a separate effective-date clause, because they are scanned images.
