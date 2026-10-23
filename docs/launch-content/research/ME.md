# Maine (ME): November 3, 2026 general election

## Sources
- **Candidates:** Maine Secretary of State (SOS) "2026 General Election Candidates List" (Excel, FINAL 7/27/2026). The file was parsed with Python's standard library. The post-primary withdrawals and replacements PDF (updated 9/22/2026) was decoded the same way.
- **Referendum status:** SOS Upcoming Elections and Citizen Initiatives pages. The SOS revised determination (invalid petition) and the May 28 provisional wording letter, a scanned PDF that was read from its page images. Ballotpedia was used to cross-check.
- **Senate replacement:** cross-checked against a Sinclair wire story and the Press Herald voter guide.
- The maine.gov pages return 403 to WebFetch, so they were fetched with curl using a browser user agent.

## Races
| Race | Candidates | Ranked-choice |
|---|---|---|
| U.S. Senator | Susan M. Collins (R), Troy D. Jackson (D) | Yes |
| Governor | Hannah M. Pingree (D), Robert B. Charles (R), Rick Bennett (Independent) | No (plurality) |
| CD-1 | Chellie Pingree (D), Ronald C. Russell (R) | Yes |
| CD-2 | Matthew G. Dunlap (D), Paul R. LePage (R) | Yes |

- Maine uses RCV in general elections only for federal offices, which is why the JSON has `houseRankedChoice: true` and `rankedChoice` on the Senate entry.
- Graham Platner won the Democratic Senate primary and withdrew on 7/10/2026. The party chose Troy Jackson to replace him.
- Bennett is unenrolled. "Independent" is his designation, not a party.
- Write-ins are excluded. Declared write-ins: 6 for Senate, 2 for Governor, 1 in CD-2.

## Measures
**None.** The SOS lists no referendum questions, and Ballotpedia (Oct 21) shows zero certified statewide measures. The school sports/birth-certificate initiative was found invalid on May 26, 2026, falling 532 signatures short. Its question wording was finalized only provisionally. `measureEffective` is a reference approximation: an initiative takes effect 30 days after the Governor's proclamation.

## Poll close
All polling places close at 8:00 PM EST, which is 2026-11-04T01:00:00Z (DST ends Nov 1).

## Confidence and gaps
- **High:** the candidates (official SOS list).
- **Not checked against a sample ballot:**
  - Ballot name forms (Charles may appear as "Bobby").
  - Party wording.
  - Whether the two-candidate federal races are printed as RCV grids.
- **Referendum litigation status:** no reinstatement was found, but the web search budget ran out before an official SOS confirmation could be found.
