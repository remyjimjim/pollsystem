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
     - Done: No
  2. Do #1 above for the prod/staging env.
     - Done: No
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
  5. - Ask Claude to estimate the emailing cost for the following scenario:  
       - App is popular and adds say 50K subscribers in 2 weeks.  What would be the 
         approximate cost to send all the registration and login link emails for said 50K users? 
  6. - When a user clicks the link to become a 'creator' and fills out the form: Add text 
       msg reply verication via user's phone number, as in, add a modal that pops up 
       and says 'A text message has been sent to your phone # (XXX-XXX-**55), please reply and your request will be submitted.'.  The modal should have a spinner and a 'Cancel' button.  
  
-  Add e2e tests to ./frontend/e2e/
