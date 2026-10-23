# Alabama — Nov 3, 2026 general election research

Researched 2026-10-23.

## Sources

Official (Alabama Secretary of State):
- 2026 general election sample ballots, all 67 counties: https://www.sos.alabama.gov/alabama-votes/2026-general-election-sample-ballots
- Statewide ballot statements for Amendments 1-4 (the "statewide back" sheet)
- 2026 Voter Guide, for poll hours (7 A.M. to 7 P.M.)
- State Canvassing Board minutes, Nov 28, 2022, for when amendments take effect

Secondary, used to cross-check:
- The Green Papers (AL 2026)
- SCOTUSblog and NPR on the June 2, 2026 map stay
- WBHM on the Aug 11 special primaries
- Alabama Reflector and Fox10 on the four amendments

## Official vs secondary

The SOS certifications of party candidates, the independent candidate and the amendments are scanned images (JBIG2). WebFetch couldn't read them and no decoder is available, so they went unread. Instead I used the SOS sample ballots, which are text PDFs. I extracted their text with a standard-library Python script and compared every county. All 67 agree on every statewide and congressional contest. That gives 16 statewide contests, 7 House districts and 4 statewide amendments.

The Green Papers agrees with the sample ballots on every candidate it lists. It gives longer name forms, such as "Caroleene Hardee Dobson" and "Jerry Lee Carl Jr."; I used the names as printed on the ballot. Party labels are as printed: "Democrat" and "Republican". No third-party or independent candidates appear in any statewide or federal race.

## Disagreements

- **Map:** the brief asked for the court-ordered map, but it is not in effect. On June 2, 2026 the Supreme Court stayed the remedial-map order, so 2026 uses the Legislature's 2023 plan. CDs 1, 2, 6 and 7 held special primaries on Aug 11. The ballots are consistent with this: Mobile and Baldwin are both entirely in CD 1.
- **Amendment 2 act number:** the ballot face prints "Act 2026- 378" with a stray space. I normalised it to match the ballot statement.

## Gaps

- I could not read the scanned certifications, so a candidate change made after the sample ballots were printed would be missed.
- Mixed-case spelling is inferred from all-caps ballots.
- The amendment effective date (2026-11-24) is estimated from past State Canvassing Board practice: 20-21 days after the election in 2022 and 2024. I did not check it against the Code.
- I did not track any map litigation after June 2.
- Write-ins and county-specific local amendments are excluded.
