# Connecticut: 2026 general election research

Researched 2026-10-23 for the Nov 3, 2026 ballot. Data: `CT.json`.

## Sources

**Official (primary)**
- Connecticut Secretary of the State town sample ballots for the Nov 3, 2026 State Election (portal.ct.gov/-/media/sots/electionservices/town_ballots/2026/…). There were no PDF tools here, so a Python standard-library script decoded them. Towns read: North Branford (CD3), Bloomfield (CD1), Torrington (CD1/CD5), Norwich (CD2), Westport, Greenwich and Fairfield (CD4), Meriden (CD5) and Oxford. Every candidate, party line and spelling comes from these ballots.

**Secondary (cross-check only)**
- CT Mirror and CT Public (Oct 20, 2026): Luke Bronin beat Rep. John Larson in the Aug 11 CD1 primary. Their list of CD1 candidates (Bronin, Chai, Green Mary Sanders) matches the ballots.

## Findings

- No U.S. Senate race this year.
- 5 statewide races: Gov/Lt Gov, Attorney General, Secretary of the State, Treasurer and Comptroller. Each has a Democratic and a Republican candidate. Several candidates also have Working Families or Independent Party (of CT) lines, and these are combined into one label per candidate, major party first.
- 5 House districts with 13 candidates in total. The only minor-party candidates are a Green in CD1 and Independent Party candidates in CD3 and CD4. George Patrick Austin, the Republican in CD2, also has the Independent line, and Jahana Hayes in CD5 also has the Working Families line.
- **No statewide ballot questions.** None appears on any sample ballot read, which covers all five districts. Norwich has a local bond question, which is left out.

## Gaps

- I found no official SOTS list of statewide questions or consolidated candidate list in readable form. The absence of measures is inferred from ballots in all five districts and from news searches. Ballotpedia's CT 2026 measures page came back empty.
- The decoder could not read the Hartford, Bridgeport, Danbury and Avon ballots because of their font encoding. Other towns covered those districts.
- Write-ins are left out (for example, Mark Stewart Greenstein in CD1).
- Measure effective date: no measures. For reference, 2026-11-25 is the approximate state canvass date (CT Const. art. XII, CGS 9-315). This date was not verified.

## Poll close

Polls are open 6:00 AM to 8:00 PM. All of Connecticut is on Eastern time and DST ends Nov 1, so polls close at 8:00 PM EST on Nov 3, which is **2026-11-04T01:00:00Z**.
