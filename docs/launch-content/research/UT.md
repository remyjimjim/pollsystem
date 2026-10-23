# Utah: 2026 general election research

Researched 2026-10-23 for the November 3, 2026 election.

## Sources

**Official (primary)**

- Lieutenant Governor's *2026 General Election Certification*, signed Aug 28, 2026. It covers candidates in ballot
  order, judicial retentions, amendment ballot text, and a statement that no statewide propositions qualified:
  https://www.utah.gov/pmn/files/1482453.pdf
- The *Amended* certification: https://www.utah.gov/pmn/files/1492833.pdf (partly readable).
- vote.utah.gov 2026 candidate filings, last updated Oct 5, 2026.
- The contingently effective constitution text on le.utah.gov (Art. VI §1, Art. XXIII §1), used for the effective
  dates.
- County notices and sample ballots (Davis, Uintah), used to cross-check and for ballot-return hours.

WebFetch could not read these PDFs. I downloaded them with curl and extracted the text with a Python stdlib script.

**Secondary:** Utah News Dispatch and Bloomberg Law, both through search snippets only (the article returned 403).
Used only for background on the map.

## Findings

- Statewide races: none. No U.S. Senate seat is up, and no statewide executive office is on the ballot. Statewide
  judicial retention covers Supreme Court Justice Pohlman and six Court of Appeals judges (noted, not modeled).
- U.S. House: all 4 districts are contested, with 4, 5, 6 and 4 candidates. The races use the court-ordered map,
  under which District 1 lies entirely within Salt Lake County. The certification and vote.utah.gov agree.
- Measures: two legislatively referred amendments.
  - Amendment A: amendment publication moves from newspapers to a method set by statute.
  - Amendment B: initiatives that raise taxes would need 60% of the vote.
- Effective dates: Amendment B on Nov 23, 2026 and Amendment A on Jan 1, 2027. 2026-11-23 is used as the
  representative date.
- Polls close at 8:00 PM MST, which is 2026-11-04T03:00:00Z.

## Gaps

- I could not work out what the amended certification changed. Its House and amendment content matches the
  original.
- The status of appeals against the map was not confirmed officially. The web search budget ran out.
- Party labels follow the certification. Some counties print abbreviations instead.
- Changes after Oct 5 were not checked.
