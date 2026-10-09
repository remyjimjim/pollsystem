import { test, expect } from '@playwright/test'
import { clearMailpit } from './mailpit'
import {
  BASE,
  STATE_INPUT,
  type Location,
  createQuestionnaireAs,
  hold,
  resolveLocation,
  searchTitlesFrom,
  seedUser,
  signInSeededUser,
} from './seed'

// Actor: admin (purview = one whole state). Journey on /admin/manage-creators:
// disabling a creator (a) disables their polls only where poll ∩ creator ∩ the
// admin's purview overlap, (b) stops them creating polls in the admin's area
// but not elsewhere, (c) is a stored switch: re-enabling one poll from the
// Polls link (Manage Polls) leaves the creator unchecked, and (d) re-checking
// the creator lifts it all.
//
// Setup via the API: a creator with nationwide access publishes a NATIONWIDE
// questionnaire, so the disable is scoped by the admin alone (= their state).
// Area and enforcement checks use the same public API the app uses; the
// admin's actions go through the UI. Read-mostly: no pre-wipe.
test.describe(`admin manages creators (${STATE_INPUT.toLowerCase()})`, () => {
  let home: Location   // the admin's state
  let away: Location   // any other state
  let creatorEmail = ''
  let adminEmail = ''
  const title = `E2E Managed Creator Poll ${Date.now()}`

  test.beforeAll(async () => {
    home = await resolveLocation()
    away = await resolveLocation(home.stateName.toLowerCase() === 'new york' ? 'colorado' : 'new york')
    creatorEmail = (await seedUser({ access: 'CREATOR', zipcode: home.zipcode })).email
    adminEmail = (await seedUser({ access: 'ADMIN', zipcode: home.zipcode, adminStateId: home.stateId })).email
    const poll = await createQuestionnaireAs(creatorEmail, title, { scopeLevel: 'NATIONAL' })
    expect(poll.ok, `seed poll: ${poll.status} ${poll.message ?? ''}`).toBe(true)
  })

  test('disable is scoped, enforced and stored; re-enabling lifts it', async ({ page }) => {
    test.setTimeout(180_000)
    await clearMailpit()
    const homeOnly = { scopeLevel: 'STATE' as const, regionIds: [home.stateId] }
    const awayOnly = { scopeLevel: 'STATE' as const, regionIds: [away.stateId] }

    // Sanity: the nationwide poll is found from both states.
    expect(await searchTitlesFrom(home.zipcode, title)).toContain(title)
    expect(await searchTitlesFrom(away.zipcode, title)).toContain(title)

    // 1. The admin sees the creator: enabled, with 1 enabled poll in their purview.
    await signInSeededUser(page, adminEmail)
    await page.goto(`${BASE}/admin/manage-creators`)
    await expect(page.getByRole('heading', { name: 'Manage Creators' })).toBeVisible({ timeout: 15_000 })
    const row = page.locator('[data-test="creator-row"]', { hasText: creatorEmail })
    const enabled = row.getByRole('checkbox', { name: `Enabled: ${creatorEmail}` })
    await expect(enabled).toBeChecked({ timeout: 15_000 })
    await expect(row.getByRole('link', { name: '1', exact: true })).toBeVisible()
    await hold(page)

    // 2. Uncheck the creator, giving a reason (optional for someone else):
    //    "No" with the reason on hover, and no enabled polls left in the purview.
    await enabled.click()
    const ask = page.getByRole('dialog').filter({ hasText: 'Why are you switching this off?' })
    await ask.getByRole('textbox').fill('E2E: spam polls')
    await hold(page)
    await ask.getByRole('button', { name: 'Switch off' }).click()
    await expect(enabled).not.toBeChecked({ timeout: 15_000 })
    await expect(row).toContainText('No')
    await expect(row.getByText('No', { exact: true })).toHaveAttribute('title', 'E2E: spam polls')
    await expect(row.getByRole('link', { name: '0', exact: true })).toBeVisible()
    await hold(page)

    // 3. Scoped: hidden from searches in the admin's state, still found elsewhere.
    expect(await searchTitlesFrom(home.zipcode, title)).not.toContain(title)
    expect(await searchTitlesFrom(away.zipcode, title)).toContain(title)

    // 4. Enforced: no new polls in the admin's state, still fine elsewhere.
    const refused = await createQuestionnaireAs(creatorEmail, `${title} (home)`, homeOnly)
    expect(refused.status).toBe(403)
    expect(refused.message ?? '').toMatch(/disabled your poll creation/i)
    const elsewhere = await createQuestionnaireAs(creatorEmail, `${title} (away)`, awayOnly)
    expect(elsewhere.ok, `away poll: ${elsewhere.status} ${elsewhere.message ?? ''}`).toBe(true)

    // 5. Polls link → Manage Polls (filtered to the creator, disabled shown):
    //    re-enable this one poll.
    await row.getByRole('link', { name: '0', exact: true }).click()
    await expect(page).toHaveURL(/\/admin\/manage-polls\?/, { timeout: 15_000 })
    await expect(page.getByText(`Creator: ${creatorEmail}`)).toBeVisible({ timeout: 15_000 })
    const pollRow = page.locator('tr', { hasText: title }).filter({ hasNotText: '(away)' })
    const pollBox = pollRow.getByRole('checkbox', { name: 'Disabled' })
    await expect(pollBox).not.toBeChecked({ timeout: 15_000 })
    await hold(page)
    await pollBox.click()
    await expect(pollRow.getByRole('checkbox', { name: 'Enabled' })).toBeChecked({ timeout: 15_000 })
    expect(await searchTitlesFrom(home.zipcode, title)).toContain(title)
    await hold(page)

    // 6. Stored switch: the creator stays unchecked, but their poll counts again.
    await page.goto(`${BASE}/admin/manage-creators`)
    await expect(enabled).not.toBeChecked({ timeout: 15_000 })
    await expect(row.getByRole('link', { name: '1', exact: true })).toBeVisible()
    expect((await createQuestionnaireAs(creatorEmail, `${title} (still home)`, homeOnly)).status).toBe(403)
    await hold(page)

    // 7. Re-check the creator: they can create in the admin's state again.
    await enabled.click()
    await expect(enabled).toBeChecked({ timeout: 15_000 })
    await expect(row).toContainText('Yes')
    const again = await createQuestionnaireAs(creatorEmail, `${title} (home again)`, homeOnly, { publish: false })
    expect(again.ok, `home again: ${again.status} ${again.message ?? ''}`).toBe(true)
    await hold(page)
  })
})
