/// <reference types="node" />
// Shared plumbing for the role/journey e2e specs.
//
// CLI tokens are parsed into env vars by playwright.config.ts — specs run in
// worker processes that don't inherit the CLI positionals, but they DO inherit
// env vars the config sets in the main process:
//   state=<name>  -> E2E_STATE   full state name or 2-letter initial (default colorado)
//   county=<name> -> E2E_COUNTY  a county within the state (default: first county)
//   keep=yes      -> E2E_KEEP    a "registers" script skips its pre-wipe
//   wipe=yes      -> E2E_WIPE    opt in to the global post-run teardown wipe
//
// Reset model: a script whose name contains "registers" pre-wipes its user(s)
// in beforeAll (unless keep). Scripts that reuse the seed never wipe. The global
// teardown leaves data by default (wipe only on wipe=yes) so seeded users
// persist for the next script.
import { expect, type Page } from '@playwright/test'
import { fetchMagicLink } from './mailpit'

export const BASE = 'http://localhost:3000'
export const API = 'http://localhost:8080'

export function isTruthy(v: string | undefined): boolean {
  return v != null && ['yes', '1', 'true', 'on'].includes(v.toLowerCase())
}

export const STATE_INPUT = (process.env.E2E_STATE ?? 'colorado').trim()
export const COUNTY_INPUT = (process.env.E2E_COUNTY ?? '').trim()
export const KEEP = isTruthy(process.env.E2E_KEEP)

/** Email-safe form of a state name: "new york" -> "newyork". */
export function stateTag(state: string = STATE_INPUT): string {
  return state.toLowerCase().replace(/[^a-z0-9]/g, '') || 'state'
}

/**
 * Canonical seeded-user address, matching the bulk seeder
 * (register-users): zzz{i}-test{role}-{state}@protonmail.com.
 */
export function seededEmail(role: string, i = 1, state: string = STATE_INPUT): string {
  return `zzz${i}-test${role}-${stateTag(state)}@protonmail.com`
}

export interface Location {
  stateId: number
  stateName: string
  countyName: string
  zipcode: string
}

/**
 * Resolve a REAL zipcode (and the state id) for the requested state — and, when
 * county=<name> is given, from that specific county. register-checkout rejects
 * any zipcode absent from county_zips, so we can't invent one. Matches the state
 * by full name or initial (case-insensitive) via the public geography API, and
 * returns the lowest zipcode (deterministic).
 */
export async function resolveLocation(
  stateInput: string = STATE_INPUT,
  countyInput: string = COUNTY_INPUT,
): Promise<Location> {
  const states = (await getJson(`${API}/api/states`)) as Array<{ id: number; name: string; initial: string }>
  const want = stateInput.toLowerCase()
  const state = states.find((s) => s.name.toLowerCase() === want || s.initial.toLowerCase() === want)
  if (!state) {
    throw new Error(
      `Unknown state '${stateInput}'. Pass a full name or 2-letter initial ` +
        `(e.g. state=iowa or state=IA). Known initials: ${states.map((s) => s.initial).join(', ')}`,
    )
  }

  let countyName = ''
  let zips: Array<{ zipcode: string }>
  if (countyInput) {
    const counties = (await getJson(`${API}/api/counties?state_id=${state.id}`)) as Array<{ id: number; name: string }>
    const cwant = countyInput.toLowerCase()
    const county = counties.find((c) => c.name.toLowerCase() === cwant)
    if (!county) {
      throw new Error(
        `Unknown county '${countyInput}' in ${state.name}. Examples: ` +
          `${counties.slice(0, 8).map((c) => c.name).join(', ')}${counties.length > 8 ? ', …' : ''}`,
      )
    }
    countyName = county.name
    zips = (await postJson(`${API}/api/zipcodes`, { countyIds: [county.id] })) as Array<{ zipcode: string }>
  } else {
    // No county → every zip in the state (robust: always yields one if seeded).
    zips = (await postJson(`${API}/api/zipcodes`, { stateIds: [state.id] })) as Array<{ zipcode: string }>
  }
  if (zips.length === 0) throw new Error(`No zipcodes seeded for ${countyName || state.name}.`)
  return { stateId: state.id, stateName: state.name, countyName, zipcode: zips[0].zipcode }
}

async function getJson(url: string): Promise<unknown> {
  const res = await fetch(url)
  if (!res.ok) throw new Error(`GET ${url} failed: ${res.status}`)
  return res.json()
}
async function postJson(url: string, body: unknown): Promise<unknown> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok) throw new Error(`POST ${url} failed: ${res.status}`)
  return res.json()
}

/** Wipe every zzz-prefixed user (and everything anchored to them). Dev-only. */
export async function resetTestUsers(prefix = 'zzz'): Promise<void> {
  const res = await fetch(`${API}/api/dev/reset-test-users?emailPrefix=${encodeURIComponent(prefix)}`, {
    method: 'POST',
  })
  if (!res.ok) throw new Error(`reset-test-users failed: ${res.status} ${await res.text().catch(() => '')}`)
}

/** Seed a published questionnaire to find + answer; returns its unique title. */
export async function seedQuestionnaire(prefix = 'zzz'): Promise<{ id: number; title: string }> {
  const res = await fetch(`${API}/api/dev/seed-questionnaire?emailPrefix=${encodeURIComponent(prefix)}`, {
    method: 'POST',
  })
  if (!res.ok) throw new Error(`seed-questionnaire failed: ${res.status} ${await res.text().catch(() => '')}`)
  return (await res.json()) as { id: number; title: string }
}

/**
 * Register a brand-new paid member through the UI (pay-first flow; mock Stripe
 * in local / local-docker) and sign in via the Mailpit magic link. Leaves the
 * page on the signed-in landing (Logout visible).
 */
export async function registerAndSignIn(
  page: Page,
  user: { email: string; phone: string; zipcode: string },
): Promise<void> {
  await page.goto(`${BASE}/register`)
  await page.getByLabel('Email').fill(user.email)
  await page.getByLabel('Phone').fill(user.phone)
  await page.getByLabel('Zipcode').fill(user.zipcode)
  await page.getByRole('button', { name: /Continue to payment/ }).click()
  await page.waitForURL(/checkout=success/, { timeout: 30_000 })
  await expect(page.getByText(/Payment received/)).toBeVisible({ timeout: 10_000 })

  const href = await fetchMagicLink(user.email)
  await page.goto(href)
  await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible({ timeout: 30_000 })
}

/**
 * Sign in as an ALREADY-SEEDED user via /login → Mailpit magic link (no
 * registration, no reset). Throws a helpful error if the account doesn't exist
 * — reuse scripts should run after the seeder.
 */
export async function signInSeededUser(page: Page, email: string): Promise<void> {
  await page.goto(`${BASE}/login`)
  await page.getByLabel('Email').fill(email)
  await page.getByRole('button', { name: 'Email me a sign-in link' }).click()

  const sent = page.getByText('Check your email.')
  const noAccount = page.getByText(/account for that email|register first/i)
  await Promise.race([
    sent.waitFor({ state: 'visible', timeout: 15_000 }).catch(() => {}),
    noAccount.waitFor({ state: 'visible', timeout: 15_000 }).catch(() => {}),
  ])
  if (await noAccount.isVisible()) {
    throw new Error(`Seeded user '${email}' not found — run the seeder first (e.g. register-users state=…).`)
  }

  const href = await fetchMagicLink(email)
  await page.goto(href)
  await expect(page.getByRole('button', { name: 'Logout' })).toBeVisible({ timeout: 30_000 })
}
