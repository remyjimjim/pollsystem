#!/usr/bin/env python3
"""
create-polls.py: create an election and its ballot measures, or a list of
elections, from a content file ("election" + "ballotMeasures", or "elections").

Creates them as DRAFTS (nothing goes live) and prints their ids and links.
Publishing is a separate, deliberate step: publish the election from its edit
page (its draft measures go live with it), or rerun with --publish-election ID.

Usage:
  scripts/launch-content/create-polls.py CONTENT.json --api URL --token-file FILE
  scripts/launch-content/create-polls.py --api URL --token-file FILE --publish-election ID [ID ...]
  scripts/launch-content/create-polls.py --api URL --token-file FILE --retitle RENAMES.json
  scripts/launch-content/create-polls.py HOUSE.json --api URL --token-file FILE --link-ballots

A content file with "includeElectionTitled" makes each of its elections a
district ballot that includes that (statewide) election, found by title among
your polls. --link-ballots applies the file's titles and includes to elections
that already exist (found by "title" or "formerTitle"): a published one is
reopened, saved, and published again, with its candidates unchanged.

  --api         backend base URL, e.g. http://localhost:8080 or https://pollsystem-backend.fly.dev
  --token-file  a file holding the creating account's JWT (git-ignored; never printed)
  --site        the website, for the printed links (default: matches --api)

The token is the "token" value in the browser's localStorage after signing in.
"""
import argparse, json, sys, urllib.request, urllib.error

def call(api, token, method, path, body=None):
    req = urllib.request.Request(api.rstrip('/') + path, method=method,
                                 data=None if body is None else json.dumps(body).encode(),
                                 headers={'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return json.loads(r.read() or b'null')
    except urllib.error.HTTPError as e:
        sys.exit(f"✗ {method} {path} -> {e.code}: {e.read().decode(errors='replace')[:400]}")

def main():
    p = argparse.ArgumentParser()
    p.add_argument('content', nargs='?')
    p.add_argument('--api', required=True)
    p.add_argument('--token-file', required=True)
    p.add_argument('--site')
    p.add_argument('--publish-election', type=int, nargs='+')
    p.add_argument('--retitle', help='rename polls: {"renames": [{"type", "from", "to"}]} (a super token)')
    p.add_argument('--link-ballots', action='store_true', help='apply titles and includes from CONTENT to existing elections')
    a = p.parse_args()
    token = open(a.token_file).read().strip()
    site = (a.site or ('http://localhost:3000' if 'localhost' in a.api else 'https://ballotbox.us.com')).rstrip('/')

    if a.retitle:
        # Super-only edit. It also sets the close date, so send the current one
        # back unchanged (leaving it out would clear it). Already-renamed polls
        # are skipped, so this can be rerun safely.
        rows = call(a.api, token, 'GET', '/api/super/polls')
        for r in json.load(open(a.retitle))['renames']:
            done = [x for x in rows if x['type'] == r['type'] and x['title'] == r['to']]
            found = [x for x in rows if x['type'] == r['type'] and x['title'] == r['from']]
            if done and not found:
                print(f"· already \"{r['to']}\"")
                continue
            if len(found) != 1:
                sys.exit(f"✗ expected one {r['type']} titled \"{r['from']}\", found {len(found)}")
            x = found[0]
            call(a.api, token, 'PUT', f"/api/super/polls/{r['type']}/{x['id']}", {'title': r['to'], 'closeDate': x['closeDate']})
            print(f"✓ {r['type']} {x['id']}: \"{r['to']}\"")
        return
    if a.publish_election:
        for eid in a.publish_election:
            e = call(a.api, token, 'POST', f'/api/polls/elections/{eid}/publish?confirmed=true')
            print(f"✓ published election {e['id']} \"{e['title']}\" (its draft ballot measures go live with it)")
        return
    if not a.content:
        p.error('give a content file, or --publish-election ID')

    c = json.load(open(a.content))
    include = []
    if c.get('includeElectionTitled'):
        mine = call(a.api, token, 'GET', '/api/creator/polls')
        hits = [p for p in mine if p['type'] == 'Election' and p['title'] == c['includeElectionTitled']]
        if len(hits) != 1:
            sys.exit(f"✗ expected one election titled \"{c['includeElectionTitled']}\", found {len(hits)}")
        include = [hits[0]['id']]

    if a.link_ballots:
        mine = call(a.api, token, 'GET', '/api/creator/polls')
        by_title = {p['title']: p for p in mine if p['type'] == 'Election'}
        for election in c['elections']:
            p = by_title.get(election['title']) or by_title.get(election.get('formerTitle'))
            if p is None:
                sys.exit(f"✗ no election titled \"{election['title']}\" or \"{election.get('formerTitle')}\"")
            cur = call(a.api, token, 'GET', f"/api/polls/elections/{p['id']}")
            if cur['title'] == election['title'] and cur.get('includedElectionIds') == include:
                print(f"· {p['id']} already \"{election['title']}\"")
                continue
            was_published = cur['status'] == 'PUBLISHED'
            if was_published:
                call(a.api, token, 'POST', f"/api/creator/polls/election/{p['id']}/reopen")
            body = {'pollTypeId': cur['pollTypeId'], 'title': election['title'], 'date': cur['date'],
                    'scopeLevel': cur['scopeLevel'], 'regionIds': cur['regionIds'], 'closeDate': cur['closeDate'],
                    'candidates': [{'name': x['name'], 'affiliation': x['affiliation'], 'officeName': x['officeName']} for x in cur['candidates']],
                    'includedElectionIds': include}
            call(a.api, token, 'PUT', f"/api/polls/elections/{p['id']}", body)
            if was_published:
                call(a.api, token, 'POST', f"/api/polls/elections/{p['id']}/publish?confirmed=true")
            print(f"✓ {p['id']} \"{election['title']}\" includes {include}{' (republished)' if was_published else ''}")
        return

    # Rerunnable: anything already among your polls (same type and title) is
    # skipped, so a second run never creates duplicates.
    existing = {(p['type'], p['title']): p['id'] for p in call(a.api, token, 'GET', '/api/creator/polls?showArchived=true')}
    # Either one election with its ballot measures, or a list of elections.
    for election in c.get('elections', []) + ([c['election']] if 'election' in c else []):
        election = {k: v for k, v in election.items() if k != 'formerTitle'}
        if include: election['includedElectionIds'] = include
        if ('Election', election['title']) in existing:
            e = {'id': existing[('Election', election['title'])], 'title': election['title']}
            print(f"· election {e['id']} \"{e['title']}\" already exists")
            continue
        e = call(a.api, token, 'POST', '/api/polls/elections', election)
        print(f"✓ election {e['id']} \"{e['title']}\" ({len(election['candidates'])} candidates), DRAFT")
        print(f"    {site}/creator/polls/election/{e['id']}/edit")
    for m in c.get('ballotMeasures', []):
        if ('BallotMeasure', m['title']) in existing:
            print(f"· ballot measure {existing[('BallotMeasure', m['title'])]} \"{m['title']}\" already exists")
            continue
        b = call(a.api, token, 'POST', '/api/polls/ballot-measures', {**m, 'electionId': e['id']})
        print(f"✓ ballot measure {b['id']} \"{b['title']}\", DRAFT")
        print(f"    {site}/creator/polls/ballot-measure/{b['id']}/edit")

if __name__ == '__main__':
    main()
