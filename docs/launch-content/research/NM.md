# New Mexico: 2026 general election research

Researched 2026-10-23 for the November 3, 2026 election.

## Sources

**Official (primary)**

- SOS Candidate Portal, *2026 General Election Contest/Candidate List* (candidateportal.servis.sos.state.nm.us/CandidateList.aspx).
- SOS publication *Statewide Constitutional Amendments and General Obligation Bonds*. The sos.nm.gov copy returned 404, so the identical copy posted by Roosevelt County was used.
- Santa Fe County sample ballot (style 01) and master ballot list.
- Enacted joint resolutions on nmlegis.gov: HJR 2 (2025), which is Amendment 1, and HJR 1 (2026), which is Amendment 3.

None of these PDFs could be read through WebFetch. They were saved with curl and their text was decoded with Python's stdlib (zlib plus a glyph-ID decoder).

**Secondary:** the Ballotpedia NM 2026 measures page.

## Findings

- **Statewide:** 8 races. U.S. Senate, Gov / Lt Gov (one ticket entry), SoS, AG, Auditor, Treasurer and Land Commissioner each have one Democrat and one Republican. Court of Appeals Position 1 has a single candidate, Kris Houghton (D). The Supreme Court retentions (Thomson, Bacon) and the Court of Appeals retention (Hanisee) are noted but not modeled.
- **U.S. House:** 3 districts, each with D vs R.
- **Measures:** 7. Constitutional Amendments 1–4 cover the pocket veto and veto messages, school election timing, regent nominating committees, and legislator pay. Bond Questions 1–3 are $21.6M for senior facilities, $19.3M for libraries and $352.2M for higher education, special schools and tribal schools.
- **Effective date:** on certification by the State Canvassing Board, which meets the third Tuesday after the election. The representative date used is 2026-11-24.
- **Poll close:** 7:00 PM MST, which is 2026-11-04T02:00:00Z. The whole state is on Mountain time.

## Disagreements

- **Amendment 3:** the SOS publication says "six months". The county ballot, the master list, HJR 1 and Ballotpedia all say "twelve months", and the ballot wording is used.
- **Amendment 1:** the SOS publication reverses the clause order. The ballot follows the HJR 2 title, and that wording is used.
- **Bond amounts:** Ballotpedia's figures ($30M / $20M / $230M) are stale. The official ballot amounts are used.

## Gaps

- Names are all-caps on the ballot and are title-cased here without accents (e.g. "Ben R Lujan").
- No CD-2 county sample ballot was checked.
- The canvass statute (NMSA 1-13-15) could not be fetched; that rule is from memory.
- No changes after the portal snapshot were checked; search budget ran out.
