# Deploying the frontend to Cloudflare Pages

The Vue SPA is served as a static build from **Cloudflare Pages** (free, global
CDN). API calls stay same-origin: a Pages **Function** proxies `/api/*` to the
Fly backend, so there's no CORS and no build-time API URL to manage.

```
  browser ──▶ codehutch.org ──▶ Cloudflare Pages
                 │  static SPA (dist/) + SPA history fallback (_redirects)
                 └─ /api/* ──▶ Pages Function ──▶ https://pollsystem-backend.fly.dev  (Fly)
  Stripe   ──────────────────────────────────▶ backend /webhooks/stripe  (direct, not via Pages)
  magic link email ──▶ https://codehutch.org/auth/magic-link  (SPA route)
```

## Files in the repo

- `frontend/public/_redirects` — SPA history fallback (`/* → /index.html 200`) so
  deep links / hard refreshes work. Vite copies it into `dist/`.
- `frontend/functions/api/[[path]].js` — the `/api/*` → backend proxy. Reads the
  `BACKEND_ORIGIN` env var.

Local dev is unaffected: `npm run dev` still uses Vite's `/api` → `localhost:8080`
proxy; the Pages Function only runs on Cloudflare.

## One-time setup

1. **Create the Pages project**: Cloudflare dashboard → **Workers & Pages** →
   **Create** → **Pages** → **Connect to Git** → select the `pollsystem` repo.

2. **Build settings**:
   | Setting | Value |
   |---|---|
   | Production branch | `main` |
   | Root directory | `frontend` |
   | Framework preset | Vue (or "None") |
   | Build command | `npm run build` |
   | Build output directory | `dist` |

3. **Environment variables** (Settings → Environment variables):
   | Name | Value |
   |---|---|
   | `BACKEND_ORIGIN` | `https://pollsystem-backend.fly.dev` (or `https://api.codehutch.org`) |

4. **Deploy.** Pages runs `npm run build` in `frontend/`, serves `dist/`, and
   deploys `frontend/functions/` automatically.

5. **Custom domain**: Pages project → **Custom domains** → add `codehutch.org`.
   If the domain's DNS is on Cloudflare, it's wired automatically; otherwise add
   the CNAME Cloudflare shows you at your registrar.

6. **Point the backend's magic links at the frontend** — set on Fly so email
   links resolve to the SPA:
   ```bash
   flyctl secrets set -a pollsystem-backend APP_BASE_URL=https://codehutch.org
   ```

## Verify

- `https://codehutch.org` loads the SPA; a hard refresh on `/polls/search` still
  works (SPA fallback).
- `https://codehutch.org/api/polls/search/suggestions` returns JSON (Function →
  backend proxy working).
- Register → the magic-link email points at `https://codehutch.org/auth/magic-link`.

## Alternative: separate API subdomain + CORS

If you'd rather not proxy, put the backend on `api.codehutch.org`, give the SPA a
build-time `VITE_API_BASE_URL=https://api.codehutch.org` (and have the axios setup
read it), and enable CORS for `https://codehutch.org` on the backend. That's a
frontend + backend code change; the Pages Function proxy above avoids both.
