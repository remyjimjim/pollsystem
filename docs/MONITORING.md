# Monitoring

Two alarms, both emailing **remyjimjim@proton.me** (decided 2026-10-21):

| What | Catches | Where it lives |
|---|---|---|
| **Uptime** (UptimeRobot) | the site or the backend down or unreachable | uptimerobot.com, outside our stack |
| **Error alerts** (in the backend) | errors the backend logs while it's up | `ops/ErrorAlerts.kt`, Fly secret `ALERT_EMAIL` |

## Uptime: UptimeRobot

Free plan, checks every 5 minutes. Being outside GitHub, Fly and Cloudflare,
it notices when they're down too.

Sign up at uptimerobot.com, add **remyjimjim@proton.me** as the alert contact,
then add these monitors (**+ New monitor**):

1. **Prod site**
   - Type: HTTP(s)
   - URL: `https://ballotbox.us.com/`
   - Interval: 5 minutes
2. **Prod API, end to end** (site → Cloudflare proxy → backend → database)
   - Type: Keyword
   - URL: `https://ballotbox.us.com/api/polls/search?title=uptime-check`
   - Keyword: `[`, alert when the keyword is *not* found. A healthy answer is a
     JSON list, empty here.
   - Interval: 5 minutes
3. **Prod backend health**
   - Type: Keyword
   - URL: `https://pollsystem-backend.fly.dev/actuator/health`
   - Keyword: `UP`, alert when *not* found.
   - Interval: 5 minutes

Monitors 2 and 3 wake the prod backend every 5 minutes, so it never sleeps.
From launch (Oct 26, 2026) that's moot: `min_machines_running = 1` keeps a
machine up anyway.

Optionally, the same three for staging (`staging.pollsystem.pages.dev`,
`pollsystem-backend-staging.fly.dev`), so a broken staging is noticed before a
prod deploy.

## Error alerts: in the backend

Every **ERROR** the backend logs is collected and emailed as one digest, **at
most every 15 minutes**. A burst is one email, not hundreds. Up to 30 errors
are listed, each with the start of its stack trace; beyond that the digest
counts how many more.

- On when the Fly secret `ALERT_EMAIL` is set; off when it isn't (locally, in
  tests). The startup log says which: "Error alerts on…" or "Error alerts off…".
- The subject names the app: `[pollsystem-backend] 3 errors`, or
  `[pollsystem-backend-staging] …` for staging.
- Errors from the mail code itself are ignored, so a mail outage can't cause an
  alert loop. A mail outage shows up in `flyctl logs` instead.
- Sent through the same email service as sign-in links (Resend).

Set or change the address:

```
flyctl secrets set ALERT_EMAIL=remyjimjim@proton.me -a pollsystem-backend-staging
flyctl secrets set ALERT_EMAIL=remyjimjim@proton.me -a pollsystem-backend
```

When an alert arrives: `flyctl logs -a <app>` shows the full log around it.
