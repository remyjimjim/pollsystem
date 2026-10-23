# Missouri: 2026 general election research

Researched 2026-10-23.

## Sources

- **Candidates (official):** Secretary of State, *Certification of Candidates
  and Party Emblems*, General Election Nov 3, 2026, dated Aug 25, 2026
  (`2026GeneralElectionCertifiedCandidates.pdf`). WebFetch couldn't read it,
  so I downloaded it and decoded the text streams with Python's standard
  library. Names and parties come from this document.
- **Measures (secondary):** the SOS 2026 Ballot Measures page was blocked
  by Cloudflare for curl, and WebFetch timed out twice. The ballot titles
  come from the Ballotpedia pages for each measure, which cite that SOS page.
  St. Louis Public Radio's Sep 22 voter guide independently confirms the same
  five measures: Amendments 3, 6, 7, 8 and Proposition A.
- **Redistricting (news):** STLPR and AP (via ADN, Sep 26).

## Findings

- No U.S. Senate race. **State Auditor** is the only statewide partisan
  office, with 3 candidates (R, D, L).
- **8 House districts**, each with an R, D and L candidate (24 total).
- **The 2022 map is in effect.** The Aug 4 primaries were held under the
  2025 map (HB 1). On Sep 3 the Missouri Supreme Court held the referendum
  valid, which suspended HB 1, and on Sep 26 the U.S. Supreme Court declined
  to intervene. The nominees did not change. Proposition A asks voters
  whether to approve HB 1, which would apply from 2028.
- **5 measures.** Amendment 3's wording is the court-of-appeals version
  (Dec 4, 2025). Amendment 6 was put on the ballot by Supreme Court order
  (Sep 3, 2026). Proposition A's title is the appellate revision
  (Apr 30, 2026).
- **Effective date:** amendments take effect 30 days after the election,
  which is Dec 3, 2026.
- **Poll close:** 7:00 PM CST = 01:00 UTC Nov 4.

## Disagreements

- Ballotpedia shows the Amendment 3 ballot title without a fiscal-note line.
  A secondary source quotes a fiscal note that includes Greene County.

## Gaps / unconfirmed

- The official SOS measures page was not read, and the titles were not
  checked against a sample ballot.
- The `ballotTitle` field holds the summary question only. It omits
  Missouri's fiscal note summary.
- Post-certification candidate changes were checked only in news coverage
  (none found). The party wording on the ballot face was not verified.
- Judicial retention (Supreme Court Judge Paul C. Wilson, appellate judges)
  is not modeled.
