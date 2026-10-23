# Oregon: 2026 general election research

Researched 2026-10-23 for the November 3, 2026 election.

## Sources

**Official (primary)**

- Oregon Secretary of State, *Upcoming Elections*: https://sos.oregon.gov/voting/Pages/current-election.aspx
  This page names Governor and U.S. Senator as the statewide races and says "There are no statewide ballot
  measures on the ballot this election."
- 2026 General Election State Voters' Pamphlet, "List of Candidates & Measures". The Secretary of State calls
  it a complete listing of filed federal and state candidates, in ballot order. Seven county editions
  (Books 1, 4, 8, 10, 13, 17, 19) together cover all six districts:
  https://sos.oregon.gov/elections/Voters-Pamphlet/Pages/Voters-Pamphlet-English.aspx

WebFetch cannot read the pamphlet PDFs. The text was pulled locally by decompressing the streams with
Python's standard library and decoding them through the fonts' ToUnicode maps. The output read cleanly, and
the statewide lists are the same in all seven books.

**Secondary (cross-check only)**

- The Green Papers (FEC data to Oct 14) has the same candidates and fusion nominations.
- Wikipedia has the Labor Commissioner primary result and Measure 120's move to May.

## Findings

- Statewide: U.S. Senator (4 candidates) and Governor (3).
- Labor Commissioner was decided in the May 19 primary: Christina Stephenson won a majority (63.2%). No
  statewide judicial race is on the November ballot.
- U.S. House: all 6 districts are contested, with 15 candidates in total (2 or 3 per district).
- Fusion labels are combined in the pamphlet's order, e.g. "Democratic, Working Families" and
  "Republican, Libertarian". "Independent" means the Independent Party of Oregon. The pamphlet prints
  "Democrat"; it is spelled "Democratic" here.
- Measures: none. Measure 120 (transportation taxes) was moved to the May primary and failed. Rule for
  reference: an approved measure takes effect 30 days after the election (Or. Const. art. IV, sec. 1(4)(d)),
  so 2026-12-03 is used as the representative date.
- Return deadline: ballots must be at a drop box or county office by 8 PM on Nov 3. That is 8 PM PST,
  2026-11-04T04:00:00Z. Malheur's Mountain-time sliver closes an hour earlier, at 03:00Z.

## Gaps / unconfirmed

- The ballot-face party wording and the per-district rotation of candidate order were not checked against a
  county sample ballot.
- The Labor Commissioner result comes from secondary sources; the official May results abstract was not read.
- No candidate changes after the pamphlet went to print were checked in official sources.
