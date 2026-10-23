# Alaska (AK) research notes

Researched 2026-10-23. All data comes from elections.alaska.gov: the 2026 General
Election candidate list (updated Sep 25, 2026), the official English sample ballots
(HD1-JD1, HD20-JD3 and HD40-JD2 decoded), the petition status pages, the Attorney
General's ballot-language letters, and the official primary and 2024 results CSVs.
Web search was unavailable, so nothing was cross-checked against Ballotpedia or news.

## Voting system
The top-four primary and ranked-choice general election (2020 Ballot Measure 2) are
**in effect** on Nov 3, 2026. The 2024 repeal failed 160,124 to 160,861. The 2026
sample ballots ask voters to rank 1st to 5th choice for every candidate race. A new
repeal (24ESEG) is Ballot Measure No. 2. If it passes, it takes effect only after
certification, so it cannot change this election. Entries carry `rankedChoice` /
`houseRankedChoice`.

## Ballot
- **U.S. Senate (4):** Heikes, Peltola, Daniel J. Sullivan Jr., Dan S. Sullivan (incumbent).
- **Gov / Lt Gov (4 tickets):** Bronson/Church, Kreiss-Tomkins/Johnson, Taylor/English, Wilson/Shower.
- **U.S. House at-large (4):** Begich (incumbent), Hafner, Hill, McDermott.
- **Measures (2):** No. 2 (24ESEG, repeal top-four/RCV and 2020 campaign-finance rules);
  No. 3 (25USCV, only U.S. citizens may vote). No. 1 (23RCF2, contribution limits) was on
  the August primary ballot and passed.

The ballot prints names as "Last, First"; they are rewritten as "First Last". Party
labels are the printed affiliation, such as "Registered Republican". Write-ins and
judicial retention questions (including Justice Jude Pate) are left out.

## Effective date
Under Alaska Constitution art. XI, sec. 6, an initiative becomes law 90 days after the
election is certified. The certification target is 2026-11-25, so the file uses
**2027-02-23**.

## Poll close
Polls are open 7 a.m. to 8 p.m. local time. That is 8 PM AKST (05:00Z) on most of the
mainland. The latest close is in the western Aleutians, at 8 PM HST, which is
**2026-11-04T06:00:00Z**.

## Gaps / unconfirmed
- No affiliation is printed for Daniel J. Sullivan Jr., although the candidate list says
  Registered Republican. The party is left blank, and the reason was not found.
- Withdrawals changed the fields from the raw primary top four: Leslie (Senate) and
  Begich/Hnilicka (Governor) are gone, and Heikes and Taylor/English moved up. The full
  history of who withdrew was not traced.
- The official name-placement PDF is a scanned image and could not be read.
- The effective date depends on when certification actually happens.
