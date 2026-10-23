# Arizona — Nov 3, 2026 general election research

Researched 2026-10-23. The data is in `AZ.json`: 7 statewide offices, 9 House districts, 8 propositions.

## Sources

**Official (tried, blocked):** azsos.gov (`/elections`, `/elections/ballot-measures`) and apps.arizona.vote returned HTTP 403 to both WebFetch and curl, so **nothing here was read directly from the Secretary of State**.

**Official (read):** Arizona Legislature statutes:
- https://www.azleg.gov/ars/16/00648.htm (state canvass on the third Monday after the general election)
- https://www.azleg.gov/ars/16/00651.htm (the Governor proclaims approved measures "forthwith" after the canvass)

**Secondary (primary data source):** Ballotpedia
- https://ballotpedia.org/Arizona_2026_ballot_measures and each proposition's page. The ballot language is quoted from the SOS "2026 Ballot Language" page, accessed 2026-07-21.
- https://ballotpedia.org/Arizona_state_executive_official_elections,_2026, plus the Governor, Corporation Commission and other race pages.
- The nine district pages, `Arizona's_Nth_Congressional_District_election,_2026`.

**Secondary (cross-checks):**
- https://www.thegreenpapers.com/G26/AZ (cites SOS candidate data)
- https://www.azmed.org/eight-statewide-ballot-measures-set-for-arizonas-november-election
- Fox 10 voter guide and Corporation Commission debate coverage
- Axios Phoenix (search snippet only; the page returned 403)
- Vote Solar

## Disagreements

- **School-spending measure number:** Ballotpedia and Axios say Prop 320; AZ Med says Prop 143. Ballotpedia shows a different Prop 143 (school-district labor organizations) as *not* on the ballot, so 320 is used.
- **Candidate names:** The Green Papers uses ballot-style nicknames, such as Alex Kolodin, Terry Ruiz, Jo Mendoza, Abe Hamadeh, Dani Sterbinsky and Teri Ann Hourihan. Ballotpedia uses the full names, which the JSON uses.
- **Mike Cease (Green, Corporation Commission):** listed by Ballotpedia and Fox 10, but Vote Solar describes a two-Democrat, two-Republican field.
- **John Giles (Lt. Gov. running mate):** Ballotpedia lists "No party preference"; The Green Papers lists "Republican and Independent".
- **Party lineups otherwise agree** between Ballotpedia and The Green Papers for every statewide race and all 9 House districts. The Green Papers did not cover the Corporation Commission.

## Gaps

- None of the candidates or ballot text could be verified against azsos.gov or the publicity pamphlet. Check these by hand before publishing.
- The exact ballot spellings and party labels as printed are unconfirmed.
- The ballot language reflects SOS text as of July 21. No court-ordered rewrites were found for the 8 measures on the ballot; the only rewrite found was for Prop 212, which is off the ballot.
- The `ballotTitle` field holds the heading, Official Title and Descriptive Title. The official yes/no effect statements are omitted.
- **Effective date:** measures take effect on the Governor's proclamation, which comes after the canvass. 2026-11-23 (the canvass date) is used as a representative date; the actual proclamation date is unknown.
- **Poll close:** 7:00 PM MST, which is 2026-11-04T02:00:00Z.
- **Out of scope / not applicable:** judicial retention races are not enumerated, and there is no U.S. Senate race in 2026.
