# Launch agenda (5 days)

Written 2026-10-20 from `todo.md` and `docs/Notes.txt`, plus a check of the
code and the deployments that day.

## Where things stand (2026-10-20)

- **Prod is out of step with itself.** The prod frontend (Cloudflare Pages)
  publishes automatically from `main`, so it has every change. The prod backend
  (Fly `pollsystem-backend`) was last released **5 Oct 2026** (v9): it lacks two
  weeks of work and **9 database migrations, V27–V35**. Those migrations have
  run cleanly on staging.
- **No legal pages** (terms, privacy, refunds), **no rate limiting**, and **no
  monitoring or error alerting** exist in the app yet.
- `BuildAndDeploy.bash` has no `prod` command; prod is deployed by hand
  (`docs/DEPLOYING-FLY.md`).
- Prod sleeps when idle (`min_machines_running = 0`); set it back to 1 at
  launch.
- The results privacy threshold is **4** (changed from 10 on 2026-10-20).

## 0. Settle the name (day 1, before anything that depends on it)

The domain, the email sender's reputation, the Stripe business name and
receipts, and the legal pages all carry the name. Changing it after launch
means re-warming the email domain, redirects, and confused users.

- Decide: `surveysays.buzz` (in `docs/ENVIRONMENTS.md`) or another
  (candidates in `docs/Notes.txt`: shout.us.com, shout.locker, votevetter,
  votesitter, hearyehearye.com).
- Rename only what people see: the domain, the "Poll System" brand text,
  the email sender, the Stripe names, the legal pages, and the help library.
- Keep the internal names (repo, `pollsystem` Fly apps, database, code
  packages): users never see them, and renaming them adds risk.
- If it can't be settled in a day or so, launch as `surveysays.buzz` and rename
  later, accepting the costs above.

## 1. First (days 1–2): make prod real and safe to take money

1. **Bring prod up to date.**
   - Take a Neon restore point.
   - Deploy the backend to prod (V27–V35 run on startup) and smoke-test it.
   - Optional: add a guarded `prod` command to `BuildAndDeploy.bash`.
2. **Domain and email: start on day 1.**
   - Point the DNS at the app and set `APP_BASE_URL`.
   - Verify the sending domain in Resend: SPF, DKIM, DMARC.
   - **Start warming up the sending domain now.** Sign-in is by emailed link
     only, so links in spam mean nobody gets in, and warm-up takes days.
3. **Live payments.**
   - Live Stripe keys, a live price, and a live webhook endpoint and secret.
   - Save the Customer Portal in live mode.
   - Make one real purchase and refund, end to end.
4. **At launch:** prod `min_machines_running` back to **1**.

## 2. Second (days 2–3): legal and basic protection

- **Terms of Service, Privacy Policy, refund/cancellation policy.** A paid
  subscription needs them, Stripe expects them, and the app holds emails,
  phones and zipcodes. Link them from checkout and the footer.
- **Deleting an account:** a stated way to do it, even if it starts as "email
  us".
- **Rate limiting** on sign-in link requests, sign-up and search. The existing
  IP-rule code is a starting point.
- **Monitoring:** an uptime check and error alerts.
- **Backups:** confirm Neon's point-in-time restore window, and do one restore
  drill.

## 3. Third (days 3–5): content and a final pass

- **Launch content:** real creators and admins with real polls, so day one isn't
  an empty site. Demo data (`scripts/demo/seed.bash`) must **not** go to prod.
- **A full walkthrough on a real phone against prod:** sign up → pay → vote →
  results → creator report.
- **Feature freeze one day before launch,** fixes only.
- **Tidy `todo.md`:** the help library and the Stripe e2e test are marked "Done:
  No" but are done.
- **Confirm the privacy threshold** (now 4) is the number you want on day one.

## After launch (not blocking)

- FEC candidate lookup, then state/local options (`todo.md`).
- Help library: spotlights, hover screenshots, tours, videos; translating it
  into the other 8 languages.
- A creator overview across all their polls.
- "Until" dates when disabling a creator grant.
- SMS confirmation for creator requests (`todo.md` item 6).
- Server-side logout; the admin "scalpel".
- From `Notes.txt`: a moderation event log, a within/outside chart, an
  accessibility pass, a translation pass, phone-number lookup, a
  data-retention policy, and the "dreams" (trend maps, "how did my county
  answer" pages, a read-only results API).
