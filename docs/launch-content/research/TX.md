# Texas — Nov 3, 2026 general election research

Researched 2026-10-23.

## Sources

Official:
- SOS Ballot Certification Report, 2026 November General Election, certified Aug 28, 2026: https://www.sos.texas.gov/elections/forms/2026-ballot-cert.pdf
- SOS advisory 2026-22 (ballot preparation): https://www.sos.texas.gov/elections/laws/advisory2026-22.shtml
- VoteTexas (polls open 7:00 a.m. to 7:00 p.m. statewide): https://www.votetexas.gov/voting/where.html
- Legislative Reference Library, constitutional amendment election dates: https://lrl.texas.gov/legis/ConstAmends/electiondates.cfm

Secondary, used to cross-check:
- Ballotpedia: U.S. House TX 2026, state executive 2026, Supreme Court/CCA 2026, intermediate appellate 2026, Texas elections 2026
- The Green Papers: https://www.thegreenpapers.com/G26/TX
- Election Code §67.012 text (texas.public.law)

## Official vs secondary

All candidate names and parties come from the official certification. It is a 1,395-page PDF. WebFetch couldn't read it, so I pulled the text out of its content streams with a small Python script, then grouped the candidates by office across all 254 counties. The certification prints names in capitals; I converted them to mixed case.

Ballotpedia and the Green Papers agree with the certification in all 38 House districts and in every statewide race.

"No statewide measures" is backed by two official sources. The LRL lists no amendment election after Nov 4, 2025, and the SOS advisory mentions no propositions. Ballotpedia's 2026 Texas page also shows no state ballot measures.

Poll hours are from VoteTexas. The Mountain Time counties, El Paso and Hudspeth, come from the Green Papers and general knowledge.

## Disagreements (certification used)

- Land Commissioner Libertarian: the certification has "NEILL SNIDER"; Ballotpedia has "Neil Snider".
- Comptroller Republican: the certification has "DON HUFFINES"; Ballotpedia has "Donald Huffines" and marks him as the incumbent. I did not verify incumbency, and it doesn't affect the ballot.
- Minor name forms differ between sources, such as middle names and suffixes (Fletcher, Menefee, Haynes III). I used the certification's form.

## Gaps

- I found no post-Aug 28 amendment to the certification, so a late withdrawal is possible but unconfirmed.
- Mixed-case spelling is inferred from an all-caps source.
- `measureEffective` is a placeholder because there are no measures. Under Tex. Const. art. XVII §1, amendments take effect on the canvass. That rule is from secondary knowledge; I did not fetch the constitution text.
- Write-in candidates are excluded.
