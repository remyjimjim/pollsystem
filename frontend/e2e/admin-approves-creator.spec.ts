import { test, expect } from '@playwright/test'
import { clearMailpit, waitForEmail } from './mailpit'
import {
  BASE,
  STATE_INPUT,
  type Location,
  devUser,
  hold,
  latestCreatorRequest,
  resolveLocation,
  seedUser,
  signInSeededUser,
} from './seed'

// Actor: admin. Journey: a member can't open the poll wizard, so they request
// creator access for their whole state; the admin the request was routed to
// approves it from the Creator Requests queue; the member is emailed "You are
// now a Creator!" and the poll wizard opens for them.
//
// Requests route to ONE admin whose access covers the state (fewest pending
// wins), and test data persists between runs, so an admin from an earlier run
// may win. The spec seeds a fresh state admin (so someone always covers the
// state), then asks the API which admin got the request and signs in as them.
// Read-mostly: actors are seeded via the API, so per convention no pre-wipe.
test.describe(`admin approves a creator request (${STATE_INPUT.toLowerCase()})`, () => {
  let loc: Location
  let memberEmail = ''
  let seededAdminEmail = ''

  test.beforeAll(async () => {
    loc = await resolveLocation()
    memberEmail = (await seedUser({ zipcode: loc.zipcode })).email
    seededAdminEmail = (await seedUser({ access: 'ADMIN', zipcode: loc.zipcode, adminStateId: loc.stateId })).email
  })

  test('member requests, admin approves, member can now create polls', async ({ browser }) => {
    test.setTimeout(180_000)
    await clearMailpit()

    const memberCtx = await browser.newContext()
    const adminCtx = await browser.newContext()
    try {
      const member = await memberCtx.newPage()

      // 1. A plain member is turned away from the poll wizard (sent home).
      await signInSeededUser(member, memberEmail)
      await member.goto(`${BASE}/creator/polls/new`)
      await expect(member).toHaveURL(`${BASE}/`, { timeout: 15_000 })
      await expect(member.getByRole('heading', { name: 'Create New Poll' })).toBeHidden()
      await hold(member)

      // 2. They request creator access for their whole state (Questionnaire).
      await member.goto(`${BASE}/creator/request`)
      await expect(member.getByRole('heading', { name: /Creator Request/i })).toBeVisible({ timeout: 15_000 })
      await member.getByRole('radio', { name: 'Whole state(s)' }).check()
      await member.getByRole('checkbox', { name: new RegExp('^' + loc.stateName) }).check()
      await member.getByRole('checkbox', { name: 'Questionnaire' }).check()
      await member.getByRole('button', { name: 'Submit Request' }).click()
      await expect(member.getByText(/request has been submitted/i)).toBeVisible({ timeout: 30_000 })
      await hold(member)

      // 3. Which admin got it? (Unassigned requests are claimable by any admin.)
      const req = await latestCreatorRequest(memberEmail)
      expect(req.status).toBe('PENDING')
      const adminEmail = req.assignedAdminId == null
        ? seededAdminEmail
        : (await devUser(req.assignedAdminId)).email

      // 4. That admin approves it from the Creator Requests queue.
      const admin = await adminCtx.newPage()
      await signInSeededUser(admin, adminEmail)
      await admin.goto(`${BASE}/admin/creator-requests`)
      await expect(admin.getByRole('heading', { name: 'Creator Requests' })).toBeVisible({ timeout: 15_000 })
      const row = admin.locator('tr', { hasText: memberEmail })
      await expect(row).toBeVisible({ timeout: 15_000 })
      await row.getByRole('checkbox').check()
      await hold(admin)
      await admin.getByRole('button', { name: 'Approve selected (1)' }).click()
      await expect(admin.getByText('1 request(s) approved.')).toBeVisible({ timeout: 15_000 })
      // Approved requests leave the pending queue.
      await expect(row).toBeHidden({ timeout: 15_000 })
      await hold(admin)

      // 5. The member is told by email…
      expect(await waitForEmail(memberEmail, 'You are now a Creator!')).toContain('Creator')

      // …and (after a fresh load, which re-reads their access) can open the wizard.
      await member.goto(`${BASE}/creator/polls/new`)
      await expect(member.getByRole('heading', { name: 'Create New Poll' })).toBeVisible({ timeout: 15_000 })
      expect((await latestCreatorRequest(memberEmail)).status).toBe('APPROVED')
      await hold(member)
    } finally {
      await memberCtx.close()
      await adminCtx.close()
    }
  })
})
