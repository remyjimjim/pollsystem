import { test, expect } from '@playwright/test'
import { clearMailpit } from './mailpit'
import {
  API,
  BASE,
  STATE_INPUT,
  type Location,
  createElectionAs,
  getPollType,
  hold,
  resolveLocation,
  seedUser,
  setPollTypeTemplate,
  signInSeededUser,
} from './seed'

// Actor: super. Journey on /super/poll-templates: edit the Election poll
// type's JSON template — invalid JSON is rejected in the editor — and switch
// the candidates widget from one-choice radios to multi-choice checkboxes.
// Every election reads the template live, so a member voting on an existing
// election now sees checkboxes and can pick both candidates (both counted).
// Then the super restores the original template.
//
// Templates are GLOBAL state (the suite runs on one worker): the original is
// saved first and restored in afterAll even if a step fails, so specs that
// expect the default radios (user-answers-election-and-measure) aren't broken.
test.describe(`super edits a poll-type template (${STATE_INPUT.toLowerCase()})`, () => {
  let loc: Location
  let superEmail = ''
  let memberEmail = ''
  let electionId = 0
  let electionTypeId = 0
  let original: Record<string, unknown> | null = null
  const electionTitle = `E2E Template Election ${Date.now()}`

  test.beforeAll(async () => {
    loc = await resolveLocation()
    superEmail = (await seedUser({ access: 'SUPER', zipcode: loc.zipcode })).email
    const pt = await getPollType(superEmail, 'Election')
    electionTypeId = pt.id
    original = pt.template
    const creatorEmail = (await seedUser({ access: 'CREATOR', zipcode: loc.zipcode })).email
    const election = await createElectionAs(creatorEmail, electionTitle, { scopeLevel: 'STATE', regionIds: [loc.stateId] }, {
      candidates: [
        { name: 'Avery Stone', affiliation: 'Independent', officeName: 'Mayor' },
        { name: 'Jordan Reyes', affiliation: 'Civic Party', officeName: 'Mayor' },
      ],
    })
    expect(election.ok, `seed election: ${election.status} ${election.message ?? ''}`).toBe(true)
    electionId = election.id!
    memberEmail = (await seedUser({ zipcode: loc.zipcode })).email
  })

  // Safety net: put the original Election template back whatever happened.
  test.afterAll(async () => {
    if (superEmail && original) await setPollTypeTemplate(superEmail, electionTypeId, original)
  })

  test('invalid JSON is rejected; a widget change reaches the voting form; restore', async ({ browser }) => {
    test.setTimeout(180_000)
    await clearMailpit()
    const modified = structuredClone(original!) as { fields: { candidates: { widget: string } } }
    expect(modified.fields.candidates.widget).toBe('selectOneRadio')
    modified.fields.candidates.widget = 'selectManyCheckbox'

    const superCtx = await browser.newContext()
    const memberCtx = await browser.newContext()
    try {
      // 1. The super opens the Election template.
      const sup = await superCtx.newPage()
      await signInSeededUser(sup, superEmail)
      await sup.goto(`${BASE}/super/poll-templates`)
      await expect(sup.getByRole('heading', { name: 'Poll Type Templates' })).toBeVisible({ timeout: 15_000 })
      const card = sup.locator('article', { has: sup.getByText('Election', { exact: true }) })
      await card.getByRole('button', { name: 'Edit' }).click()
      const editor = card.locator('textarea')
      await expect(editor).toHaveValue(/"selectOneRadio"/)
      await hold(sup)

      // 2. Invalid JSON is caught before anything is saved.
      await editor.fill('{ "type": "Election", oops }')
      await card.getByRole('button', { name: 'Save' }).click()
      await expect(card.getByText('Invalid JSON')).toBeVisible()
      await hold(sup)

      // 3. Switch the candidates widget to multi-choice checkboxes and save.
      await editor.fill(JSON.stringify(modified, null, 2))
      await card.getByRole('button', { name: 'Save' }).click()
      await expect(card.getByText('Saved.')).toBeVisible({ timeout: 15_000 })
      await expect(card.locator('pre')).toContainText('"selectManyCheckbox"')
      await hold(sup)

      // 4. A member voting on the (existing) election now gets checkboxes and
      //    can choose both candidates.
      const member = await memberCtx.newPage()
      await signInSeededUser(member, memberEmail)
      await member.goto(`${BASE}/polls/election/${electionId}`)
      await expect(member.getByRole('heading', { name: electionTitle })).toBeVisible({ timeout: 15_000 })
      await expect(member.getByRole('radio', { name: /Avery Stone/ })).toHaveCount(0)
      await member.getByRole('checkbox', { name: /Avery Stone/ }).check()
      await member.getByRole('checkbox', { name: /Jordan Reyes/ }).check()
      await hold(member)
      await member.getByRole('button', { name: 'Submit votes' }).click()
      await expect(member).toHaveURL(new RegExp(`/polls/election/${electionId}/results`), { timeout: 30_000 })
      const results = (await (await fetch(`${API}/api/polls/elections/${electionId}/results`)).json()) as {
        perCandidate: Array<{ name: string; yes: number }>
      }
      expect(Object.fromEntries(results.perCandidate.map((c) => [c.name, c.yes])))
        .toEqual({ 'Avery Stone': 1, 'Jordan Reyes': 1 })
      await hold(member)

      // 5. The super restores the original template through the editor.
      await card.getByRole('button', { name: 'Edit' }).click()
      await editor.fill(JSON.stringify(original, null, 2))
      await card.getByRole('button', { name: 'Save' }).click()
      await expect(card.getByText('Saved.')).toBeVisible({ timeout: 15_000 })
      await expect(card.locator('pre')).toContainText('"selectOneRadio"')
      expect((await getPollType(superEmail, 'Election')).template).toEqual(original)
      await hold(sup)
    } finally {
      await superCtx.close()
      await memberCtx.close()
    }
  })
})
