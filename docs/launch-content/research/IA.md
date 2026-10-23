# Iowa: 2026 general election research

Researched 2026-10-23.

## Sources

**Official**
- Iowa Secretary of State, *Candidate List, November 3, 2026 General Election*, "Last Updated 8/14/2026 12:25 PM". sos.iowa.gov blocks scripted access with a Cloudflare check, so this PDF was read from its Wayback Machine capture of 2026-10-17. It was decoded with a Python standard-library text extractor. This list is the source for all candidates and parties.
- Linn County official sample ballot, Ballot Style 67 (Hiawatha 02, IA-2). It is the source for the amendment's question, summary and full text, and it confirms all statewide candidates.
- Cerro Gordo County Auditor's candidate list (revised 9/3/2026). It matches on statewide offices, IA-2 and the amendment's full text.
- Iowa Code 49A.8 and 50.38 (legis.iowa.gov), for the canvass and effective-date rule.

**Secondary**
- The Green Papers (IA 2026): agrees on every race.
- Jasper and Crawford county pages and US Vote Foundation: polls are open 7 AM to 8 PM.

## Results
- **7 statewide offices:** U.S. Senate (3 candidates, including a Libertarian), the Governor/Lt. Governor team, and Secretary of State, Auditor, Treasurer, Secretary of Agriculture and Attorney General, each with one Republican and one Democrat.
- **4 House districts, 11 candidates.** IA-1 has a No Party candidate. IA-2 has a Libertarian and a No Party candidate.
- **1 measure:** Statewide Public Measure 1, a constitutional amendment requiring a two-thirds vote in each chamber to raise state income tax rates or create a new state income tax.
- **Polls:** close at 8 PM CST, which is 02:00 UTC on Nov 4. All of Iowa is on Central time.
- **Effective date:** 2026-11-30, the latest date for the state canvass (27 days after the election). Under Art. X, an approved amendment becomes part of the constitution.

## Gaps
- **Candidate changes after 8/14:** none were checked on the live SoS site. For IA-1, IA-3 and IA-4, no county sample ballot was read.
- **Party labels:** the ballot prints "Republican Party" and similar. The JSON uses the short SoS labels. For Bushaw, the ballot prints no party line; Bridgford's ballot face was not seen.
- **Art. X, sec. 1 wording:** not re-read from an official page, because the URL returned 404.
- **Not included:** Court of Appeals retention votes.
