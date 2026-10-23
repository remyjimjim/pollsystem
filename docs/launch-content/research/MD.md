# Maryland (MD): Nov 3, 2026 general election

Researched 2026-10-23.

## Sources

**Official (Maryland State Board of Elections):**
- General election candidate lists, HTML and per-office CSVs, last updated 10/16/2026 6:00 PM: https://elections.maryland.gov/elections/2026/General_candidates/2026_GG_statewide_candidatelist.html
- Statewide ballot questions, final language: https://elections.maryland.gov/elections/2026/ballot_questions.html
- Poll hours, 7 AM to 8 PM: https://elections.maryland.gov/voting/election_day_questions.html
- Md. Const. Art. XIV, Sec. 1 (effective-date rule), from the Maryland Manual (MSA).

The HTML pages and CSVs were downloaded and decoded with Python's standard library. I checked that the ballot text matches the official page word for word.

**Secondary sources (cross-check only):**
- State Court Report and Conduit Street (MACo) on the Supreme Court of Maryland's Sept 3, 2026 order on Question 3.
- AP via WTOP on the CD5 primary.
- Wikipedia and Ballotpedia search snippets on the governor field.

## Counts

- **Statewide offices (3):**
  - Governor/Lt. Governor: 4 tickets (D, R, Green, Working Class Party)
  - Comptroller: 2 candidates
  - Attorney General: 2 candidates
- **No U.S. Senate race** is on the ballot.
- **U.S. House:** 8 districts, 20 candidates in total.
- **Statewide questions:** 3 constitutional amendments.

## Disagreements and judgment calls

- **Write-ins:** the SBE list marks three write-in filers as "Active" (CD1 Shlikas, CD3 Coffman, CD5 Hall). Their filing type is "Write-In", so they are excluded.
- **Withdrawn and failed petition candidates** are excluded.
- **Secondary-source gaps:** secondary snippets did not mention the Working Class Party ticket (White / Permut). The official list shows it as Active, so it is included.
- **AP's "three independents" in CD5:** these match the SBE's write-in and failed-petition filers. None of them is printed on the ballot.
- **Question 3 wording:** the language is the substitute text ordered by the Supreme Court of Maryland on Sept 3, 2026. The amendment does not change the 2026 map.

## Gaps

- **Effective date:** amendments take effect when the Governor proclaims them, and no statute fixes that date. The date 2027-01-15 is an estimate based on the 2024 precedent (Jan 17, 2025). That precedent date comes from a search summary, because the governor's proclamation PDF could not be read.
- **Ballot face not checked:** party wording and name order were not compared against a sample ballot.
- **Q1/Q2 explanations:** the SBE page prints "A vote FOR/AGAINST" explanations for these two questions. I did not confirm whether they appear on the ballot itself.
- **Later changes:** nothing after the SBE list update of 10/16 was checked.

## Times

All of Maryland is on Eastern time. Polls close at 8:00 PM EST on Nov 3, which is 2026-11-04T01:00:00Z.
