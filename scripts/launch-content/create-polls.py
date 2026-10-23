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
    a = p.parse_args()
    token = open(a.token_file).read().strip()
    site = (a.site or ('http://localhost:3000' if 'localhost' in a.api else 'https://ballotbox.us.com')).rstrip('/')

    if a.publish_election:
        for eid in a.publish_election:
            e = call(a.api, token, 'POST', f'/api/polls/elections/{eid}/publish?confirmed=true')
            print(f"✓ published election {e['id']} \"{e['title']}\" (its draft ballot measures go live with it)")
        return
    if not a.content:
        p.error('give a content file, or --publish-election ID')

    c = json.load(open(a.content))
    # Either one election with its ballot measures, or a list of elections.
    for election in c.get('elections', []) + ([c['election']] if 'election' in c else []):
        e = call(a.api, token, 'POST', '/api/polls/elections', election)
        print(f"✓ election {e['id']} \"{e['title']}\" ({len(election['candidates'])} candidates), DRAFT")
        print(f"    {site}/creator/polls/election/{e['id']}/edit")
    for m in c.get('ballotMeasures', []):
        b = call(a.api, token, 'POST', '/api/polls/ballot-measures', {**m, 'electionId': e['id']})
        print(f"✓ ballot measure {b['id']} \"{b['title']}\", DRAFT")
        print(f"    {site}/creator/polls/ballot-measure/{b['id']}/edit")

if __name__ == '__main__':
    main()
