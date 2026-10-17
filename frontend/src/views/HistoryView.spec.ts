import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'
import axios from 'axios'
import en from '@/i18n/en.json'
import HistoryView from './HistoryView.vue'

vi.mock('axios')

const ROWS = [
  { id: 3, type: 'questionnaire', pollId: 7, pollTitle: 'Vaccines', event: 'PUBLISHED', at: '2026-10-17T12:00:00Z',
    by: 'you', byEmail: null, details: 'Close date: none → 2027-01-15 18:00 UTC' },
  { id: 2, type: 'election', pollId: 9, pollTitle: 'MidTerm', event: 'SWITCHED_OFF', at: '2026-10-16T12:00:00Z',
    by: 'ADMIN', byEmail: 'a@b.c', details: 'Everywhere' },
  { id: 1, type: 'questionnaire', pollId: 7, pollTitle: 'Vaccines', event: 'PUBLISHED_BEFORE_HISTORY', at: '2026-10-15T00:00:00Z',
    by: 'automatic', byEmail: null, details: null },
]

async function mountAt(path: string) {
  vi.mocked(axios.get).mockResolvedValue({ data: ROWS })
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/history', component: HistoryView }] })
  await router.push(path)
  const i18n = createI18n({ legacy: false, locale: 'en', messages: { en } })
  const w = mount(HistoryView, { global: { plugins: [router, i18n] } })
  await flushPromises()
  return { w, router }
}

afterEach(() => vi.resetAllMocks())

describe('HistoryView', () => {
  it('lists what happened, when, by whom, and the details', async () => {
    const { w } = await mountAt('/history')
    expect(axios.get).toHaveBeenCalledWith('/api/poll-history', { params: {} })
    const rows = w.findAll('[data-test="history-row"]').map(r => r.findAll('td').map(td => td.text()))
    expect(rows[0].slice(1)).toEqual(['Vaccines · Questionnaire', 'Published', 'You', 'Close date: none → 2027-01-15 18:00 UTC'])
    expect(rows[1].slice(1, 4)).toEqual(['MidTerm · Election', 'Switched off', 'An admin (a@b.c)'])
    expect(rows[2][0]).toBe('—')                                  // no invented date
    expect(rows[2][2]).toBe('Published before history began')
  })

  it('picking a poll shows just that poll (and the address says so)', async () => {
    const { w, router } = await mountAt('/history')
    await w.find('[data-test="history-poll"]').setValue('questionnaire:7')
    await flushPromises()
    expect(router.currentRoute.value.query).toEqual({ type: 'questionnaire', id: '7' })
    expect(axios.get).toHaveBeenLastCalledWith('/api/poll-history', { params: { type: 'questionnaire', id: 7 } })
    expect(w.findAll('th').map(t => t.text())).not.toContain('Poll')   // one poll: no Poll column
  })
})
