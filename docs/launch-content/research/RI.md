# Rhode Island: 2026 general election research

Researched 2026-10-23 for the November 3, 2026 election.

## Sources

**Official.** vote.ri.gov / vote.sos.ri.gov and elections.ri.gov returned a Cloudflare challenge (HTTP 403) to both
WebFetch and curl all session. That blocked the candidate list, the sample ballots and the Voter Information Handbook.
The official sources that could be read:

- The enacted FY2027 budget, H 7127 Sub A, Article 5 (rilegislature.gov PDF, decoded locally with Python's stdlib).
  It gives the five bond projects, the amounts, majority approval and the effective-date rule.
- Department of State press releases on sos.ri.gov. The Sept 29 release confirms five statewide bond questions.

**Secondary.** Ballotpedia (race pages, the measures list and the quoted ballot text of each question) and Wikipedia
race pages (wikitext). The two agree on every race's field.

## Findings

- Statewide: U.S. Senate (3 candidates), Governor (5), Lt. Governor (2), Secretary of State (1, unopposed),
  Attorney General (2), General Treasurer (2).
- U.S. House: CD1 has 3 candidates and CD2 has 2.
- Measures: Questions 1-5, all general obligation bonds totalling $600M: higher education $275M, housing $120M,
  economic development $100M, the State History Center and preservation grants $50M, and environment/recreation $55M.
- Effective: the bond sections take effect when the Board of Elections certifies majority approval (Art. 5 §13).
  The date 2026-11-24 is an estimate.
- Polls close at 8:00 PM EST, which is 2026-11-04T01:00:00Z. The whole state is on Eastern time.
- The primary was Wed Sept 9. Foulkes unseated Gov. McKee in the Democratic primary.

## Gaps / unconfirmed

- **Printed names.** These follow Wikipedia's general-election boxes, which appear to be drawn from the SOS candidate
  search (for example "Aaron C. Guckian", "C.D. Reynolds", "Alan Leonard Gordon"). Ballotpedia uses common forms such
  as "Christopher Reynolds". For the CD1 independent, Ballotpedia's "Pedro Soares de Souza" is used over Wikipedia's
  "Pedro DeSouza".
- **Party labels** on the ballot face were not checked. Full party names and "Independent" are used.
- **Question text** is Ballotpedia's quote of the SOS plain-language ballot label ("Shall the act passed by the General
  Assembly at the January 2026 session…"). It was not verified against an official sample ballot. Its projects and
  amounts match the enacted Article 5.
- **Late changes.** No withdrawals or substitutions after the secondary sources' last updates were checked.
