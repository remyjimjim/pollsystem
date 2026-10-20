// Cloudflare Pages Function — proxy /api/* to the Spring Boot backend.
//
// The SPA calls the API with relative paths (e.g. `/api/auth/me`). On Pages the
// static site has no backend, so this Function forwards every /api/* request to
// the backend origin. Because the browser only ever talks to its own origin
// (surveysays.buzz), there is no CORS and no need for a build-time API base URL.
//
// Configure the backend origin as a Pages environment variable:
//   BACKEND_ORIGIN = https://pollsystem-backend.fly.dev   (or api.surveysays.buzz)
// and, for per-IP rate limits, the secret shared with the backend:
//   PROXY_SECRET = <the same value as the backend's PROXY_SECRET Fly secret>
//
// Note: the Stripe webhook (POST /webhooks/stripe) is NOT under /api and is not
// proxied here — Stripe posts directly to the backend URL (see DEPLOYING-FLY.md).
export async function onRequest(context) {
  const { request, env } = context

  const backend = env.BACKEND_ORIGIN
  if (!backend) {
    return new Response('BACKEND_ORIGIN is not configured for this Pages project', {
      status: 500,
    })
  }

  const url = new URL(request.url)
  const target = backend.replace(/\/+$/, '') + url.pathname + url.search

  // Preserve method, headers (incl. Authorization: Bearer <jwt>), and body.
  const proxied = new Request(target, request)
  proxied.headers.set('X-Forwarded-Host', url.host)
  proxied.headers.set('X-Forwarded-Proto', 'https')

  // The visitor's real IP, for the backend's per-IP rate limits: the backend
  // otherwise sees Cloudflare's address for everyone. Signed with the shared
  // PROXY_SECRET (a Pages env var here, a Fly secret on the backend), so it
  // can't be forged by calling the backend directly. Never pass on a visitor's
  // own copies of these headers.
  proxied.headers.delete('X-Client-IP')
  proxied.headers.delete('X-Proxy-Secret')
  const clientIp = request.headers.get('CF-Connecting-IP')
  if (env.PROXY_SECRET && clientIp) {
    proxied.headers.set('X-Client-IP', clientIp)
    proxied.headers.set('X-Proxy-Secret', env.PROXY_SECRET)
  }

  // Make the proxied request genuinely same-origin from the backend's view.
  // Browsers attach `Origin` to every POST/PUT/DELETE (even same-origin), and
  // forwarding it makes Spring Security treat the call as cross-origin and
  // reject it with 403 "Invalid CORS request" (its CORS allowlist is only the
  // local dev origin). Stripping these keeps the proxy transparent and
  // origin-agnostic — no backend CORS entry needed per frontend domain.
  proxied.headers.delete('Origin')
  proxied.headers.delete('Access-Control-Request-Method')
  proxied.headers.delete('Access-Control-Request-Headers')

  return fetch(proxied, { redirect: 'manual' })
}
