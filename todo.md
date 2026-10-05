# TODO List:

- Local-stack launcher script.
  - Done: Yes
  - Notes: `scripts/BuildAndDeploy.bash` brings up the whole local stack —
    starts the Postgres + Mailpit docker-compose services if they're not
    already running (waiting for Postgres to accept connections), then runs
    the backend (`gradlew bootRun`, `local` profile) and frontend (`vite dev`)
    concurrently, with a Ctrl-C teardown that leaves the containers up.
    Commands: `local` (default) / `test` / `test-secrets` / `infra` / `status`
    / `down`; env toggles `SKIP_FRONT` / `SKIP_BACK`. Mailpit is now a
    docker-compose service too.
  - Next: item #1 below (encrypted-creds injection) layers on top of this.

- Staging / test deploy environment.
  - Done: Yes
  - Notes: `./scripts/BuildAndDeploy.bash test` deploys to staging — backend to
    Fly app `pollsystem-backend-staging` (`backend/fly.staging.toml`, Neon
    `staging` branch), frontend by pushing the `staging` git branch (Cloudflare
    Pages serves `https://staging.pollsystem.pages.dev`; a Preview-scoped
    `BACKEND_ORIGIN` points its `/api` proxy at the staging backend).
    `test-secrets` imports staging secrets from the OS keychain
    (`service=pollsystem-fly-staging`) into Fly. Details + the first-deploy
    IP-allocation gotcha are in `docs/DEPLOYING-FLY.md` and `docs/ENVIRONMENTS.md`.
  - Follow-ups (optional): attach `staging.surveysays.buzz`; add Stripe test
    keys to staging; scale staging to a single machine.

- Rotate the exposed Neon `neondb_owner` password.
  - Done: Yes
  - Notes: Rotated on BOTH Neon branches (prod + staging carry independent
    role passwords — a branch inherits the parent's at creation but a reset on
    one never touches the other), pushed each new value into its Fly app, and
    verified both healthy + DB-connected. The local creds cheatsheet
    (`docs/Pollsystem specific commands.txt`) was scrubbed and gitignored;
    the old password was never in git. See DEVLOG 2026-09-05.

- Come up with a process to:
  1. Sets login values for ./docker-compose.yml based on values found in   
     encrypted file at root dir, e.g., ./.creds.txt for local env.
     - Done: yes.  creds are in keyring. 
  2. Do #1 above for the prod/staging env.
     - Done: yes
     - Notes:  Learned that a test/staging environment is probably needed cuz stripe   
               testing, etc. UPDATE: a staging env now exists (see "Staging /
               test deploy environment" above); its secrets currently come from
               the OS keychain via `test-secrets`, not an encrypted file — #1's
               encrypted-creds approach could still replace that later.
  3. Add "bad-words.json" file listing bad-word:value and replace:value name:value pairs.  
     Add 'make comments public? Y/N (Noone will know who's comment it is)' checkbox for each "Comments" box and when user submits poll submission and the checkbox is checked, the comments are searched for bad words and
     if found then focus goes to offending comment box and modal pops up with question
     "Can I replace bad-word with good-word?" and does that for each bad-word found.
     - Done: No
  4. - Find out if it's possible to capture claude CLI chat output including claude's output
       and questions, my answers and bash commands to a file called 
       sessions.transcript.md. 
       
       Done: Yes.  Two independent transcripts (both git-ignored, in the repo root):
       - /export (typed in the Claude Code chat) -> sessions.transcript.md.  Covers
         the CURRENT session only, and bash commands are collapsed.
       - scripts/claude-expand (run from the repo root, on the host or in the Dev
         Container) -> sessions.transcript.expanded.md.  Reads Claude Code's raw
         session logs (~/.claude/projects/<project>/*.jsonl), NOT
         sessions.transcript.md, so /export isn't needed first.  Covers ALL
         sessions, oldest first, with bash commands inlined; the file is rebuilt
         from scratch each run, so re-running never duplicates or loses anything.
         Options: --latest (current session only), --help.
       - Host shortcut: ln -sf "$PWD/scripts/claude-expand" ~/.local/bin/claude-expand
         (run once from the repo root) so plain `claude-expand` uses the repo
         version.  Without it, ~/.local/bin/claude-expand still runs the old
         ~/.claude/expand-session.py, which keeps only the newest session.
       - Tested in the Dev Container: works (needed the full python3, now in
         .devcontainer/Dockerfile).  Moved to pollsystem/scripts: done.
       - Summary:
         - Shortcut (from any directory; checks where you are, cds to the repo,
           builds, then ls -lh's the file):
             scripts/claude-expand -host        # in a host terminal
             scripts/claude-expand -container   # in the Dev Container
             scripts/claude-expand -help        # usage; extra opts pass through, e.g. -host --latest
         - To run from host do (in a host terminal):
             cd "/home/remy/Dev/Projects/Java/Spring/SpringBoot+Kotlin+Psql+Vue/pollsystem"
             scripts/claude-expand          # or just `claude-expand` once the ln -sf above is done
             ls -lh sessions.transcript.expanded.md
         - To run from Dev Containers do (in VS Code's integrated terminal; the repo is
           mounted at the same path, so the commands are the same):
             cd "/home/remy/Dev/Projects/Java/Spring/SpringBoot+Kotlin+Psql+Vue/pollsystem"
             scripts/claude-expand
             ls -lh sessions.transcript.expanded.md
         - Optional, either place: type /export in the Claude Code chat first if you also
           want sessions.transcript.md (current session only).
  5. - Ask Claude to estimate the emailing cost for the following scenario:  
       - App is popular and adds say 50K subscribers in 2 weeks.  What would be the 
         approximate cost to send all the registration and login link emails for said 50K users? 
       Done: Yes (estimated 2026-10-05; prices from resend.com/pricing and
       aws.amazon.com/ses/pricing that day).
       - Emails: 1 sign-in link per sign-up (sent when the Stripe webhook creates
         the account; Stripe's own receipts cost us nothing) + ~0.5-1 extra links
         (15-min link expired / re-requested / second device). The 90-day login
         means ~no repeat-login emails in the window. Creator/admin mail is tiny.
         => ~1.5-2 emails/user = ~75K-100K emails (worst case ~150K).
       - Resend (what we use; free tier is out at 100/day):
         75K ~ $35-42.50 | 100K = $35 (100K plan) | 150K ~ $80 ($35 + $0.90/1K over).
         Amazon SES floor: ~$8-15 ($0.10/1K). Not worth switching at this scale.
       - Bottom line: ~$35-80 for the launch fortnight, then ~20-25K/month
         (one link per user per device every 90 days) = Resend's $20/mo plan.
       - Bigger risks than price: warm up the sending domain before launch (a cold
         domain jumping to thousands/day lands in spam); check Resend's per-second
         rate limit (unverified) and ask for a raise before a launch spike; a launch
         straddling two billing months may fit two cheaper plans.
  6. - When a user clicks the link to become a 'creator' and fills out the form: Add text 
       msg reply verication via user's phone number, as in, add a modal that pops up 
       and says 'A text message has been sent to your email, please reply and your request will be submitted.'.  The modal should have a spinner and a 'Cancel' button.  
  
-  Add e2e tests to ./frontend/e2e/:
   - Run some recordings and translate to uml for claude.
   - Done.  No.  Some but not all.
   
-  Add a help/documentation library for things like:
   - How to find polls by title, zipcode, state or county.
   - How to be a creator along with creator tasks and reports
   - How to be an admin along with admin tasks and reports
   - How to be a super along with tasks and reports
   - Done: No.
