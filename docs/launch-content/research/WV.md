# West Virginia: Nov 3, 2026 general election research

Researched 2026-10-23. The data is in `WV.json`: 1 statewide office (U.S. Senate), 2 House districts and 1 constitutional amendment.

## Sources

**Official (primary):**
- The WV Secretary of State candidate listing at https://candidates.wvsos.gov. It is a single-page app, so it was queried through its JSON API (`/candidate-web-api/candidates`, candidateType `R` for regular). Every candidate and party comes from it, using the `candidateBallotName` field and the party descriptions it gives (REPUBLICAN / DEMOCRAT / CONSTITUTION). Names were converted to title case.
- The SOS elections FAQ gives the polling hours: 6:30 AM to 7:30 PM.
- W. Va. Code §§3-11-2, 3-11-4 and 3-11-6 (code.wvlegislature.gov) cover the ballot form and the effective date.

**Secondary:**
- The amendment's title, summary of purpose and full text come from the SOS public notice as printed in the Moorefield Examiner (2026-08-04). wvlegislature.gov returned HTTP 500 for every request, so the official SJR 9 text could not be read.
- Ballotpedia News and States Newsroom were used as cross-checks.

## Findings

- **U.S. Senate:** Capito (R), Fetty Anderson (D) and S. Marshall Wilson (Constitution). Rio Phillips is a write-in and is left out.
- **WV-1:** Miller (R), George (D) and Fox-Spencer (Constitution).
- **WV-2:** Moore (R) and Parsi (D).
- **Judicial:** Supreme Court of Appeals and Intermediate Court of Appeals races were decided on the nonpartisan May 12 primary ballot, so they are omitted. The general-election judicial races are local unexpired Circuit and Family Court seats.
- **Amendment 1 (SJR 9):** restricts voting to U.S. citizens who are state citizens. The House passed it 97-0 and the Senate 34-0. It is the only amendment on the ballot. The homestead amendment (HJR 42) died without concurrence.
- **Effective date:** under §3-11-6 an amendment is in force "from the date of such ratification". The date used is 2026-11-03. The SOS proclamation is due on day 25, about Nov 28.
- **Poll close:** 7:30 PM EST, which is 2026-11-04T00:30:00Z. The whole state is on Eastern time.

## Gaps

- The amendment wording was not checked against an official SOS page or a county sample ballot.
- Printed party labels were not checked against a sample ballot.
- The meaning of "ratification" (election day or proclamation) is not settled.
- The web-search budget ran out before a full Ballotpedia cross-check, and changes after 10-23 were not checked.
