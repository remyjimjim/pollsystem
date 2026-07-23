// Cloudflare Pages Function — proxy /api/* to the Spring Boot backend.
//
// The SPA calls the API with relative paths (e.g. `/api/auth/me`). On Pages the
// static site has no backend, so this Function forwards every /api/* request to
// the backend origin. Because the browser only ever talks to its own origin
// (codehutch.org), there is no CORS and no need for a build-time API base URL.
//
// Configure the backend origin as a Pages environment variable:
//   BACKEND_ORIGIN = https://pollsystem-backend.fly.dev   (or api.codehutch.org)
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

  return fetch(proxied, { redirect: 'manual' })
}
