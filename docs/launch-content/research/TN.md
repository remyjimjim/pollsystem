# Tennessee: November 3, 2026 general election

Researched 2026-10-23.

## Sources

**Official (Tennessee Secretary of State, Division of Elections)**
- 2026 candidate lists page (sos.tn.gov/elections/2026-candidate-lists), with
  Excel files for Governor, U.S. Senate and U.S. House. WebFetch got HTTP 403
  from sos.tn.gov, so the pages and files were downloaded with curl. The
  `.xlsx` files were decoded with Python's standard library (`zipfile` and
  `xml.etree`). All candidate names and party labels come from these files.
- The 2026 proposed constitutional amendments page has the verbatim
  "Question" text for Amendments 1 to 3, the Attorney General's summaries,
  and the passage rule.
- The 2026 congressional redistricting page describes the new map adopted in
  May 2026.

**Secondary (used only to cross-check)**
- WSMV, Sep 28, 2026: candidates for Governor and Congress.
- 270toWin: poll-closing times.
- The Sixth Circuit opinion on 2014 Amendment 1 (via FindLaw): the Dec 8,
  2014 certification date.
- Ballotpedia snippets for Dale Braden and Billy Cody.

## Counts

- U.S. Senate: 10 candidates (R, D, 8 Independent).
- Governor: 15 candidates (R, D, 13 Independent; open seat).
- U.S. House: 9 districts, 46 candidates.
- Measures: 3 constitutional amendments, on bail exceptions, banning a state
  property tax, and crime victims' rights.

## Disagreements

- WSMV leaves out several independents in the Senate race and in TN-7, TN-8
  and TN-9. This looks like selective coverage rather than a real conflict.
- WSMV spells a TN-6 independent "Angus Perdy". The SOS file has "Angus
  Purdy", which is the spelling used.
- Ballotpedia says Dale Braden (TN-6, independent) is not on the general
  ballot. This matches the SOS list.
- Billy Cody was disqualified from the GOP primary. He is on the SOS list as
  an Independent in TN-1, which is consistent.

## Poll close

Polls close at the same moment statewide: 8:00 PM EST in Eastern counties and
7:00 PM CST in Central counties. Both are 2026-11-04T01:00Z. Daylight saving
ends Nov 1.

## Gaps

- **Primary returns not checked:** the SOS primary-result files returned 403,
  so the nominees were not checked against official primary returns.
- **No date on the candidate files:** they carry no "as of" date. Late changes
  were checked only through news.
- **Sample ballot not checked:** no sample ballot was compared for name order
  or the heading text printed above each amendment.
- **Effective date not confirmed:** Art. XI, Sec. 3 makes an approved
  amendment part of the Constitution, with no separate effective date. No
  statutory certification deadline was found. The 2026-12-07 date is only
  representative, based on the 2014 precedent.
