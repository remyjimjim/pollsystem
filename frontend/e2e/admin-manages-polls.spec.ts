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
  zipsInState,
} from './seed'

// Actor: admin (purview = one whole state). Journey on /admin/manage-polls:
// disable a poll for ONE zipcode via the block dialog → the row stays in place
// (unchecked) even with "Show disabled" off → the disable is area-aware (that
// zip no longer finds the poll, its other zip still does) → toggling "Show
// disabled" ends the stickiness (the row hides as normal) → re-enable it.
//
// Setup via the API: a creator publishes a questionnaire covering TWO zips in
// the admin's state. Read-mostly: no pre-wipe.
test.describe(`admin manages polls (${STATE_INPUT.toLowerCase()})`, () => {
  let home: Location
  let zipA = ''
  let zipB = ''
  let adminEmail = ''
  const title = `E2E Managed Poll ${Date.now()}`

  test.beforeAll(async () => {
    home = await resolveLocation()
    ;[zipA, zipB] = await zipsInState(home.stateId, 2)
    const creatorEmail = (await seedUser({ access: 'CREATOR', zipcode: zipA })).email
    adminEmail = (await seedUser({ access: 'ADMIN', zipcode: zipA, adminStateId: home.stateId })).email
    const poll = await createQuestionnaireAs(creatorEmail, title, { scopeLevel: 'ZIP', zipcodes: [zipA, zipB] })
    expect(poll.ok, `seed poll: ${poll.status} ${poll.message ?? ''}`).toBe(true)
  })

  test('zip-only disable keeps the row, is area-aware, and can be undone', async ({ page }) => {
    test.setTimeout(180_000)
    await clearMailpit()

    // 1. Find the poll on Manage Polls ("Show disabled" is off by default).
    await signInSeededUser(page, adminEmail)
    await page.goto(`${BASE}/admin/manage-polls`)
    await expect(page.getByRole('heading', { name: 'Manage Polls' })).toBeVisible({ timeout: 15_000 })
    const showDisabled = page.getByLabel('Show disabled')
    await expect(showDisabled).not.toBeChecked()
    await page.getByLabel('Title contains').fill(title)
    await page.getByRole('button', { name: 'Search' }).click()
    const row = page.locator('tr', { hasText: title })
    await expect(row.getByRole('checkbox', { name: 'Enabled' })).toBeChecked({ timeout: 15_000 })
    await hold(page)

    // 2. Uncheck → the block dialog → "This zipcode only" = zip A → Disable.
    await row.getByRole('checkbox', { name: 'Enabled' }).click()
    const dialog = page.getByRole('dialog')
    await expect(dialog).toContainText(`Block submissions for ${title}`)
    await expect(dialog.getByRole('radio', { name: 'This zipcode only' })).toBeChecked()
    await dialog.getByRole('combobox').selectOption(zipA)
    await hold(page)
    await dialog.getByRole('button', { name: 'Disable' }).click()
    await expect(dialog).toContainText('Active blocks affecting this poll')
    await expect(dialog).toContainText(zipA)
    await hold(page)
    await dialog.getByRole('button', { name: 'Close' }).last().click()
    await expect(dialog).toBeHidden()

    // 3. The row stays in place, unchecked, although disabled rows are hidden.
    await expect(row.getByRole('checkbox', { name: 'Disabled' })).not.toBeChecked({ timeout: 15_000 })
    await hold(page)

    // 4. Area-aware: zip A no longer finds it; zip B (same poll) still does.
    expect(await searchTitlesFrom(zipA, title)).not.toContain(title)
    expect(await searchTitlesFrom(zipB, title)).toContain(title)

    // 5. Changing a filter ends the stickiness: toggling "Show disabled" on and
    //    off hides the disabled row as normal.
    await showDisabled.check()
    await expect(row).toBeVisible({ timeout: 15_000 })
    await showDisabled.uncheck()
    await expect(row).toBeHidden({ timeout: 15_000 })
    await hold(page)

    // 6. Show disabled again and re-enable the poll.
    await showDisabled.check()
    await row.getByRole('checkbox', { name: 'Disabled' }).click()
    await expect(row.getByRole('checkbox', { name: 'Enabled' })).toBeChecked({ timeout: 15_000 })
    expect(await searchTitlesFrom(zipA, title)).toContain(title)
    await hold(page)
  })
})
