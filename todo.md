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
     - Still need: P1: a real Stripe test-mode checkout. It needs Stripe test keys and webhook forwarding, so
       it would be a separate, opt-in run rather than part of every CI push. Worth doing before launch, since it's the money path.
   
-  Add a help/documentation library for things like:
   - How to find polls by title, zipcode, state or county.
   - How to be a creator along with creator tasks and reports
   - How to be an admin along with admin tasks and reports
   - How to be a super along with tasks and reports
   - Decided 2026-10-08: one copy of the text in docs/manual/{viewer,user,creator,admin,super}/<process>.md,
     rendered in the app at /help; topics shown by access level; hover/click help text in the i18n files
     under a `help.<view>.<element>` section.
   - Done: No.

-  Creator reports, in phases. Privacy guardrails apply to every phase:
   - Totals only, never one row per respondent. Every breakdown hides groups under 10 (app.results.k-anonymity-threshold).
   - Hide enough cells that a hidden one can't be worked out by subtraction (e.g. state 52, counties 20 + 25 -> 7).
   - Over-time views stay coarse: whole days, grouped further for small polls.
   - Comments stay private: show a count, never the text.
   - Phase 1 (at a glance): the Creator dashboard shows each poll's Responses (with the in-area share), "Closes in N days",
     and a Results link. Help page creator/how-your-polls-are-doing.md.
     - Done: Yes (2026-10-08).
   - Phase 2: a report page for each poll, visible only to its creator:
     - participation over time (per day + running total);
     - reach: respondents per county/state in the area, and coverage, e.g. "9 of 14 counties";
     - inside vs. outside results side by side;
     - highlights per type (election leaders/margins, measure margin, questionnaire most-agreed -> most-divided);
     - engagement: answers changed, comment count.
     - Done: No.
   - Phase 3: CSV download of the report's totals, a printable summary page, and milestone emails
     ("10 responses: results by area are now visible", plus a final report at close).
     - Done: No.
   - Later: an overview across all of a creator's polls (totals, trends, most engaged areas).
     - Done: No.

-  Creator grants: an optional "until" date when disabling a grant (e.g. vacation), re-enabled automatically by a
   nightly job. Deferred 2026-10-08 (decision D); the reason box (decision C) comes first.
   - Done: No.

-  Decide: close the small-poll gap in public results.
   - The full, unfiltered results always show, even with 1-2 respondents. Anyone who knows who voted (e.g. the creator,
     three respondents in) can infer how they voted, and live updates make it easier.
   - Options: hide all results until at least 10 people have answered, and/or show a delayed snapshot instead of live.
   - Done: No.

-  Help library: show readers where bold UI terms are (e.g. **Search**, **See closed polls too**). Hybrid:
   - Spotlight links (default) for elements visible when their page first loads (most filters, buttons, checkboxes, nav):
     - The bold term links to the live page, e.g. /polls/search?spotlight=search.includeClosed.
     - Target elements get a stable marker, data-help="<view>.<element>" (same naming as the i18n help.* keys).
     - A small app-wide spotlight component waits for the element, scrolls to it, circles it in red, and shows a
       "Back to help" button beside it (not over it, so it doesn't hide the target), returning to the same spot.
   - Hover screenshots for state-dependent elements a live link can't reach:
     - e.g. search-result links/"Additional zipcodes", the privacy message, wizard steps 2+, a specific poll's
       vote/results page.
     - Hovering (or focusing/tapping) shows a screenshot with the target circled in red.
     - Generated by Playwright (the e2e specs already reach every screen), so they're regenerated when the UI changes.
   - Optionally both on one term: hover to preview, click to go there live.
   - Next layer (after the content pages exist): guided tours + videos for multi-step tasks.
     - Tours are written once, as data: a short list of steps (circle data-help target + caption).
     - Live tour: if the page is already open in another tab (found via BroadcastChannel), offer "Show me in my
       open tab"; that tab plays the steps on the real page (pointer, red circle, caption, Next/Back/Stop).
       Otherwise open a new tab, or fall back to the video.
     - Safety: a live tour only points and explains. It never clicks Submit, votes, pays, or overwrites what
       the reader typed.
     - Video: Playwright plays the same steps with a caption bar and records them (short clips, ~0.5-2 MB each,
       only for real journeys).
     - CI check: Playwright runs every tour, so a tour whose element disappears turns CI red instead of going stale.
     - Resulting layers on a bold term: spotlight (default) -> tour -> video -> hover screenshot.
   - Remy furnishes the list of bold-text candidates per page.
   - Done: No.

-  Translate the help library (docs/manual) into the app's other 8 languages (nb, fr, ja, de, es, it, pt-BR, zh-CN).
   - Written in English first; until translated, other languages show the English page with a note.
   - Wait until the English content settles, then translate per page.
   - The hover/click help (i18n `help.*` keys) is translated with the rest of the app strings.
   - Done: No.
