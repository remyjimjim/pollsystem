# New York — 2026 general election research

Researched 2026-10-23 for the Nov 3, 2026 ballot. Data: `NY.json`.

## Sources

**Official (primary)**
- NYS Board of Elections, *Amended Certification for the November 3, 2026 General Election*, dated Oct 7, 2026 (the latest version found; versions are 9/2, 9/3, 9/4, 9/17 and 10/7):
  https://elections.ny.gov/system/files/documents/2026/10/accessible-2026-general-ballot-certification-10.7.2026.pdf
  elections.ny.gov answers automated requests with a Cloudflare challenge (HTTP 403), so I read the PDF from the Wayback Machine copy (snapshot 2026-10-10). There were no PDF tools here, so a small script pulled the text out of the PDF's internal data. All statewide and House candidates and party lines come from this document.
- The election-day hours on elections.ny.gov ("Polls open 6:00am - 9:00pm") come from a search-result snippet. The Oneida County election calendar shows the same hours.

**Secondary (cross-check only)**
- Ballotpedia: no "New York 2026 ballot propositions" page exists (404), and the 2026 measures list includes nothing for NY.
- Green Papers and Wikipedia gave context.

## Disagreements

- Green Papers lists Larry Sharpe (Libertarian) as a candidate for governor. He is **not** on the official Oct 7 certification, so he is left out.
- The certification prints "Lalota" and "LiPetri Jr". Those spellings are kept, even though the congressman usually writes his name "LaLota".

## Gaps

- **Ballot proposals:** I found no 2026 statewide proposal. The archive has BOE "statewide-ballot-proposal" pages for 2024 and 2025 but none for 2026, Ballotpedia has nothing, and a news/regional guide snippet said there is no statewide proposal this year. I could not find an official statement saying there are none, so `measures` is empty and this is flagged as unconfirmed. Under NY Const. art. XIX §1, an approved amendment takes effect on January 1 after the vote, so 2027-01-01 is recorded for reference. NYC's five charter questions and county questions are local and left out.
- **Later amendments:** the certification may have been amended after Oct 7. None was found.
- **Text-extraction artifacts:** "Ghaznav i" was taken to be "Ghaznavi" (CD7) and "Ocasio - Cortez" to be "Ocasio-Cortez". I did not check these against the printed page by eye.
- **Empty fusion lines:** several Working Families and Conservative lines have no candidate in some districts and are left out.
- No U.S. Senate race in NY in 2026.

## Poll close

Polls close at 9:00 PM EST on Nov 3 (DST ends Nov 1), which is 2026-11-04T02:00:00Z.
