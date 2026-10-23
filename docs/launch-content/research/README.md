# State ballot research (2026 midterms)

Researched 2026-10-23 onwards. There is one pair of files per state:

- `<XX>.json`: the structured data, in the format below. The builder
  (`scripts/launch-content/build-state.py`) turns it into content files.
- `<XX>.md`: a short write-up covering sources, confidence and gaps.

```json
{
  "state": "Texas", "abbr": "TX",
  "pollCloseLocal": "7:00 PM", "pollCloseUtc": "2026-11-04T02:00:00Z",
  "statewide": [
    { "office": "U.S. Senator", "candidates": [ { "name": "…", "party": "Democratic" } ] }
  ],
  "house": [
    { "district": 1, "candidates": [ { "name": "…", "party": "Republican" } ] }
  ],
  "measures": [
    { "designation": "Proposition 1", "subject": "short neutral subject",
      "ballotTitle": "official ballot language, verbatim",
      "summary": "one neutral sentence", "source": "https://…" }
  ],
  "measureEffective": "2026-12-31", "measureEffectiveNote": "…rule…",
  "sources": ["https://…"],
  "notes": ["…"], "unconfirmed": ["…"]
}
```

- **House:** at-large states have a single entry with `"district": 0`.
- **Poll close:** `pollCloseUtc` is the latest poll-closing time anywhere in
  the state on Nov 3. Daylight saving ends Nov 1.
- **Candidates:** names and party labels are as printed on the ballot.
  Write-ins are left out.
