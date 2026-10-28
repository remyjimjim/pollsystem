import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import axios from 'axios'
import en from '@/i18n/en.json'
import MyBallotsView from './MyBallotsView.vue'

vi.mock('axios')
afterEach(() => vi.resetAllMocks())

async function mountWith(data: unknown[]) {
  vi.mocked(axios.get).mockResolvedValue({ data })
  const i18n = createI18n({ legacy: false, locale: 'en', messages: { en } })
  const w = mount(MyBallotsView, { global: { plugins: [i18n], stubs: { 'router-link': RouterLinkStub } } })
  await flushPromises()
  return w
}

describe('MyBallotsView (2026-10-28)', () => {
  it('lists each ballot with its date and a View link that opens it for review', async () => {
    const w = await mountWith([
      { type: 'election', id: 261, title: 'WA District 2', votedAt: '2026-10-23T17:59:25Z', open: true, enabled: true },
      { type: 'questionnaire', id: 144, title: 'Test 1', votedAt: '2026-10-18T06:08:27Z', open: false, enabled: false },
    ])
    expect(axios.get).toHaveBeenCalledWith('/api/my/ballots')
    const rows = w.findAll('[data-test="my-ballot-row"]')
    expect(rows.map(r => r.text())).toEqual([expect.stringContaining('WA District 2'), expect.stringContaining('Test 1')])
    const cell = (r: number, k: string) => rows[r].find(`[data-test="col-${k}"]`).text()
    expect([cell(0, 'closed'), cell(0, 'enabled')]).toEqual(['No', 'Yes'])
    expect([cell(1, 'closed'), cell(1, 'enabled')]).toEqual(['Yes', 'No'])
    expect(w.findAll('thead th').map(h => h.text())).toEqual(['Poll', 'Date of vote', '', 'Closed', 'Enabled?'])
    const links = w.findAllComponents(RouterLinkStub).map(l => l.props('to'))
    expect(links).toContainEqual({ path: '/polls/election/261', query: { review: '1' } })
    expect(links).toContainEqual({ path: '/polls/questionnaire/144', query: { review: '1' } })
  })

  it('says so when there is nothing yet', async () => {
    const w = await mountWith([])
    expect(w.find('[data-test="my-ballots-empty"]').text()).toContain("You haven't voted on anything yet.")
  })
})
