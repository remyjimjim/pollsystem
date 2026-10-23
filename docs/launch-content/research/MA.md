# Massachusetts: 2026 general election research

Researched 2026-10-23.

## Sources

**Official**
- Secretary of the Commonwealth, *2026 State Election Candidates*
  (sec.state.ma.us/divisions/elections/research-and-statistics/2026-state-election-candidates.htm).
  sec.state.ma.us is behind an Incapsula bot check, so I read the Wayback Machine capture from 2026-10-15. It is the source for all candidates and ballot designations.
- Secretary of the Commonwealth, *2026 Information for Voters* (English PDF, via a Wayback capture from 2026-10-16), decoded with a stdlib zlib text extractor. It gives every question's title, the "Do you approve..." sentence, the AG summary, the yes/no statements and the effective dates, plus the 7 a.m. to 8 p.m. poll hours.
- PD43+ (electionstats.state.ma.us), 2026-09-01 primary returns. Used for full names on the Governor/Lt. Gov tickets and Brensley's write-in nomination.

**Secondary (cross-check only)**
- Ballotpedia (MA elections 2026; the Senate, Governor and U.S. House pages) and Wikipedia race pages. Their candidate lists match the official list.

## Results
- **6 statewide contests, 15 candidates.** Senate has 4 candidates and the Gov/Lt Gov race has 3 tickets. AG, Secretary, Treasurer and Auditor each have 2.
- **9 House districts, 17 candidates.** MA-2, MA-5 and MA-7 each have one candidate, the Democratic incumbent.
- **9 questions.** Questions 1-8 are initiative petitions. Question 9 is a referendum on the 2024 firearms law.
- **Polls close** at 8 PM EST statewide, which is 01:00 UTC on Nov 4.

## Disagreements
- **Ballot designations:** the official list gives "Independent" for Ayyadurai, James/Kittredge and Walters, "Independent/Unenrolled" for Milleron and "Socialism and Liberation" for Tache. Ballotpedia shows "Unenrolled" and "Party for Socialism and Liberation". The JSON follows the official list.
- **Neal (MA-1):** the official page lists him without a party, which looks like an omission. He won the Democratic primary, so he is listed as Democratic.

## Gaps
- **Candidate list date:** the capture is from 2026-10-15, so changes after that are unchecked. The capture's HTML source has a commented-out "Lieutenant Governor: Shawn Oliver" entry. Wikipedia reports that Oliver suspended his campaign on Oct 18 and stays on the ballot. No news source was checked, because web search was unavailable.
- **Gov/Lt Gov names:** the official list prints the tickets by surname only. Andrea James and Kayla Kittredge's full names come from secondary sources.
- **Effective date:** 2026-12-03 is 30 days after the election, the default under Art. 48. The citation comes from general knowledge, not an official text read this session. Questions 4 and 8 take effect 2028-01-01 and Questions 5 and 6 take effect 2027-07-01, per the AG summaries. When a Question 9 "No" repeal would take effect was not confirmed.
- **Ballot wording:** not checked against a sample ballot. The official HTML ballot-questions page was unreadable.
