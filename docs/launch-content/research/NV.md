# Nevada: 2026 general election research

Researched 2026-10-23 for the November 3, 2026 election.

## Sources

**Official**

- Clark County Election Department, *All Contests and Candidates in the November 3, 2026 General Election* (dated 8/24/2026):
  https://www.clarkcountynv.gov/adobe/assets/urn%3Aaaid%3Aaem%3A1db09a57-4c1b-47e1-94d9-e7782c442571/original/as/officesup-26g.pdf
  Decoded locally with Python's stdlib, since WebFetch could not read it.
- Clark County *Facts and Figures* sheet for the 2026 general election, which lists 2 state questions.
- Nevada Constitution Art. 19 §2(4), NRS 293.260, 293.269, 293.273 and 293.395 (leg.state.nv.us).

nvsos.gov returned an Incapsula bot-block page to WebFetch and curl, so the SoS candidate list and 2026 Ballot Question Guide were unreadable.

**Secondary**

- Ballotpedia race and Question 6/7 pages.
- Fox5 (Sept 30, 2026), reporting on the SoS ballot guide.

## Findings

- **No U.S. Senate race.**
- **Statewide offices:**
  - Governor: 3 candidates
  - Lt. Governor: 3
  - Secretary of State: 4
  - Treasurer: 2
  - Controller: 2
  - Attorney General: 2
  - Supreme Court Seats B (Pickering) and D (Herndon): unopposed, but still printed (NRS 293.260(3)(a))
  - No Court of Appeals seat is listed.
- **"None of these candidates":** every statewide contest prints this line (NRS 293.269). It is not recorded as a candidate.
- **U.S. House:** all 4 districts are contested, with 3–4 candidates each. CD2 is an open seat (Amodei retired).
- **Measures:** Question 6 (abortion right) and Question 7 (voter ID). Both are second votes on initiated amendments that passed in 2024.
- **Effective date:** an amendment takes effect when the Supreme Court completes its canvass, set for the fourth Tuesday of November: **2026-11-24**.
- **Polls:** close 7:00 PM. Pacific time is UTC-8 after DST ends, so 2026-11-04T03:00Z. West Wendover keeps Mountain time, so 02:00Z there. The latest close statewide is 03:00Z.

## Disagreements

- Name forms only: Ballotpedia's "Joe Lombardo", "Kris Pickering" and "Brad Barnhill" print officially as "Joseph", "Kristina" and "Brad Lee"; official forms used.

## Gaps

- CD2 lies outside Clark County. Its names come from Ballotpedia, and the exact ballot forms are unverified.
- The question text is from Ballotpedia, which cites the SoS 2024 page; it is not from the 2026 guide. The constitution requires resubmission "in the same manner", so the wording should be unchanged.
- Changes after Aug 24, 2026 were not checked officially.
- Party labels were spelled out from the official abbreviations (DEM, REP, IAP, LPN, NPP, NP).
