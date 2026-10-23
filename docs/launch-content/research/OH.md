# Ohio: 2026 general election research

## Sources

**Official.** ohiosos.gov, and the county board sites it hosts at boe.ohio.gov, returned a 403 "Website Maintenance"
page all session, so no SOS page or PDF (including Directive 2026-45, the form of the ballot) was read. Official
sources used instead:

- County boards' certified candidate lists: Hamilton (certified 9/8/2026), Wood (certified 8/26), Franklin (list
  revised 10/16, plus its 46-day FWAB notice), Lake (8/25) and Union (100-day notice, 7/22). The PDFs were decoded locally
  with Python's standard library.
- Issue 3: the Ohio Ballot Board's certified ballot language (SOS PDF dated 08-03-2026), read from Signal Ohio's
  hosted copy of that file.

**Secondary (cross-check only).** Ballotpedia, The Green Papers, Wikipedia, and The Ballot Brief (for Tracey Tackett).

## Findings

- **Statewide (8 races):** U.S. Senate special election (unexpired term ending 1/3/2029), Gov/Lt Gov, Attorney
  General, Secretary of State, Auditor, Treasurer, and two Supreme Court seats (terms starting 1/1 and 1/2/2027). All
  five county lists agree on them.
- **House:** all 15 districts are contested. Seven of them (1, 3, 5, 8, 9, 14, 15) were checked against a county
  list. The other eight rely on the three secondary sources agreeing.
- **Measures:** one, State Issue 3, the photo-ID constitutional amendment (SJR 10). It says it takes effect
  "immediately" if approved; 2026-11-03 is used as the representative date.

## Disagreements

- **OH-9, Matthew Althaus (Libertarian):** Wikipedia lists him. Wood County's certified list and Ballotpedia do not
  ("did not make the ballot"). He is left out.
- **Auditor, Aidan Jeffery (Libertarian):** he appears on Lake County's 8/25 list but not on the later Hamilton, Wood
  or Franklin lists. He is left out.
- **OH-1:** the Libertarian nominee, John Hancock, withdrew. Hamilton certified Nathan Weise to fill the vacancy.
- **OH-11:** Wikipedia spells the Republican "Krichner". Ballotpedia and The Green Papers spell it "Kirchner", which is
  used here.

## Gaps

- There is no SOS-level confirmation of any race.
- No official list was read for districts 2, 4, 6, 7, 10, 11, 12 and 13. Exact printed names there are unverified.
- The ballot designation for independents Tackett (OH-4) and Dixit (OH-13) is left blank. Ohio prints "Nonparty
  candidate" or nothing. Greg Levy (Senate) is printed as "Other-party candidate".
- It is not settled whether Issue 3 "taking effect immediately" means election day or the date the result is
  certified.
