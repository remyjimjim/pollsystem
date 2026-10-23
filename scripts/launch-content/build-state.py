#!/usr/bin/env python3
"""
build-state.py: turn a state's research file (docs/launch-content/research/XX.json)
into content files for create-polls.py, checking it first.

  scripts/launch-content/build-state.py XX [XX ...]     build (and check)
  scripts/launch-content/build-state.py XX --check      check only

Writes, in scripts/launch-content/:
  xx-2026-statewide.json  the statewide election (statewide races) with the
                          state's ballot measures, if it has either
  xx-2026-house.json      one ballot per district, each including the statewide
                          election ("2026 Midterm <State> District N")
States with one House seat get a single ballot instead: xx-2026-ballot.json,
"2026 Midterm <State>", holding the statewide races, the House race and the
measures.

Checks: district count matches the state's seats (2020 apportionment), every
race has a candidate, no duplicate names in a race, measures have official
language and a source. Problems stop the build.
"""
import json, sys, os, re

SEATS = dict(AL=7, AK=1, AZ=9, AR=4, CA=52, CO=8, CT=5, DE=1, FL=28, GA=14, HI=2, ID=2, IL=17, IN=9, IA=4,
             KS=4, KY=6, LA=6, ME=2, MD=8, MA=9, MI=13, MN=8, MS=4, MO=8, MT=2, NE=3, NV=4, NH=2, NJ=12,
             NM=3, NY=26, NC=14, ND=1, OH=15, OK=5, OR=6, PA=17, RI=2, SC=7, SD=1, TN=9, TX=38, UT=4, VT=1,
             VA=11, WA=10, WV=2, WI=8, WY=1)
assert sum(SEATS.values()) == 435
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
P = "2026 Midterm · "
DATE = "2026-11-03"


def load(abbr):
    with open(os.path.join(ROOT, 'docs/launch-content/research', f'{abbr}.json'), encoding='utf-8') as f:
        return json.load(f)


def check(r, abbr):
    problems, warnings = [], []
    if r.get('abbr') != abbr: problems.append(f"abbr is {r.get('abbr')!r}")
    if not re.fullmatch(r'2026-11-0[34]T\d\d:\d\d:00Z', r.get('pollCloseUtc', '')): problems.append(f"pollCloseUtc {r.get('pollCloseUtc')!r}")
    house = r.get('house', [])
    want = SEATS[abbr]
    got = sorted(h['district'] for h in house)
    expected = [0] if want == 1 else list(range(1, want + 1))
    if want == 1 and got == [1]: got = [0]
    if got != expected: problems.append(f"House districts {got} (want {expected})")
    for race in [(f"District {h['district']}", h['candidates']) for h in house] + [(s['office'], s['candidates']) for s in r.get('statewide', [])]:
        name, cands = race
        if not cands: warnings.append(f"{name}: no candidates (an unopposed race left off the ballot?): its ballot carries only the statewide races")
        names = [c['name'].strip() for c in cands]
        if len(set(names)) != len(names): problems.append(f"{name}: duplicate names")
        if any(not c.get('name', '').strip() for c in cands): problems.append(f"{name}: a blank name")
    for m in r.get('measures', []):
        for k in ('designation', 'subject', 'ballotTitle', 'source'):
            if not str(m.get(k, '')).strip(): problems.append(f"measure {m.get('designation')}: no {k}")
    return problems, warnings


def cand(c, office):
    return {"name": c['name'].strip(), "affiliation": (c.get('party') or '').strip() or "No party listed", "officeName": office}


def build(abbr, ids):
    r = load(abbr)
    problems, warnings = check(r, abbr)
    for w in warnings: print(f"  ! {abbr}: {w}")
    if problems:
        for p in problems: print(f"  ✗ {abbr}: {p}")
        return False
    state, sid, close = r['state'], ids[abbr], r['pollCloseUtc']
    eff = r.get('measureEffective') or '2026-12-31'
    def election(title, cands):
        return {"pollTypeId": 1, "title": title, "date": DATE, "scopeLevel": "STATE", "regionIds": [sid], "closeDate": close, "candidates": cands}
    # The app takes one choice per office; a multi-seat race says so.
    RCV = " (ranked-choice on the ballot; pick your first choice here)"
    def office(s): return s['office'] + (f" (vote for {s['voteFor']} on the ballot; pick your top choice here)" if s.get('voteFor', 1) > 1 else "") + (RCV if s.get('rankedChoice') else "")
    hrcv = RCV if r.get('houseRankedChoice') else ""
    if r.get('houseOfficeNote'): hrcv += f" ({r['houseOfficeNote']})"
    statewide = [cand(c, office(s)) for s in r.get('statewide', []) for c in s['candidates']]
    # A short designation ("Amendment 3") gets its subject; a descriptive one
    # (North Carolina's contest titles) stands alone.
    def mtitle(m): return f"{P}{state} {m['designation']}" + (f": {m['subject']}" if len(m['designation']) <= 30 else "")
    measures = [{"pollTypeId": 3, "title": mtitle(m),
                 "summary": f"{m['ballotTitle'].strip()}\n\nIn short: {m.get('summary', '').strip()}\n\nOfficial ballot language. Source: {m['source']}",
                 "effectiveDate": eff, "closeDate": close} for m in r.get('measures', [])]
    about = f"{state}, Nov 3 2026, built by build-state.py from docs/launch-content/research/{abbr}.json (sources and gaps in {abbr}.md)."
    out = os.path.join(HERE, abbr.lower() + '-2026-')
    for suffix in ('statewide.json', 'house.json', 'ballot.json'):
        if os.path.exists(out + suffix): os.remove(out + suffix)
    if SEATS[abbr] == 1:
        h = r['house'][0]
        house = [cand(c, "U.S. Representative, At-Large" + hrcv) for c in h['candidates']]
        doc = {"_about": about + " One House seat, so one ballot for the whole state.",
               "election": election(f"2026 Midterm {state}", statewide + house)}
        if measures: doc["ballotMeasures"] = measures
        json.dump(doc, open(out + 'ballot.json', 'w'), indent=2, ensure_ascii=False)
        print(f"  ✓ {abbr}: 1 ballot ({len(statewide) + len(house)} candidates), {len(measures)} measures")
        return True
    sw_title = None
    if statewide or measures:
        sw_title = f"{P}{state}: statewide races & measures"
        doc = {"_about": about, "election": election(sw_title, statewide)}
        if measures: doc["ballotMeasures"] = measures
        json.dump(doc, open(out + 'statewide.json', 'w'), indent=2, ensure_ascii=False)
    hdoc = {"_about": about + " One ballot per district" + (f", each including \"{sw_title}\"." if sw_title else "."),
            "elections": [election(f"2026 Midterm {state} District {h['district']}",
                                   [cand(c, f"U.S. Representative, District {h['district']}" + hrcv) for c in h['candidates']])
                          for h in sorted(r['house'], key=lambda h: h['district']) if h['candidates'] or sw_title]}
    if sw_title: hdoc["includeElectionTitled"] = sw_title
    json.dump(hdoc, open(out + 'house.json', 'w'), indent=2, ensure_ascii=False)
    print(f"  ✓ {abbr}: statewide {len(statewide)} candidates in {len(r.get('statewide', []))} races, {len(measures)} measures; {len(hdoc['elections'])} district ballots")
    return True


if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    ids = json.load(open(os.path.join(HERE, 'state-ids.json')))
    ok = True
    for abbr in (a.upper() for a in args):
        if '--check' in sys.argv:
            p, w = check(load(abbr), abbr)
            for x in w: print(f"  ! {abbr}: {x}")
            for x in p: print(f"  ✗ {abbr}: {x}")
            ok &= not p
            if not p: print(f"  ✓ {abbr}: checks pass")
        else:
            ok &= build(abbr, ids)
    sys.exit(0 if ok else 1)
