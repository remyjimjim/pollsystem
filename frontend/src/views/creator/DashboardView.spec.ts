import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'
import axios from 'axios'
import en from '@/i18n/en.json'
import { useAuthStore } from '@/stores/auth'
import { AccessLevel } from '@/types'
import DashboardView from './DashboardView.vue'

vi.mock('axios')

async function mountAs(access: AccessLevel, polls: unknown[] = []) {
  vi.mocked(axios.get).mockResolvedValue({ data: polls })
  setActivePinia(createPinia())
  const auth = useAuthStore()
  auth.token = 't'
  auth.user = { id: 1, email: 'c@d.e', access, profileComplete: true } as never
  const stub = { template: '<div/>' }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      ...['/', '/creator/request', '/admin-request', '/creator/polls/new'].map(path => ({ path, component: stub })),
      { path: '/:rest(.*)*', component: stub },
    ],
  })
  const i18n = createI18n({ legacy: false, locale: 'en', messages: { en } })
  const w = mount(DashboardView, { global: { plugins: [router, i18n] } })
  await flushPromises()
  return w
}

describe('Creator DashboardView', () => {
  it('lets a creator ask for more access (more area or poll types)', async () => {
    const link = (await mountAs(AccessLevel.CREATOR)).find('a[href="/creator/request"]')
    expect(link.exists()).toBe(true)
    expect(link.text()).toBe('Request more creator access')
  })

  it('admins see it too; only non-admins see "Request admin access"', async () => {
    const w = await mountAs(AccessLevel.ADMIN)
    expect(w.find('a[href="/creator/request"]').exists()).toBe(true)
    expect(w.find('a[href="/admin-request"]').exists()).toBe(false)
  })

  describe('at a glance (creator reports, phase 1)', () => {
    const poll = (over: Record<string, unknown>) => ({
      id: 7, type: 'Questionnaire', title: 'Park hours', status: 'PUBLISHED',
      closeDate: null, createdAt: '2026-10-01T00:00:00Z', respondents: 0, inArea: 0, emails: true, ...over,
    })
    const cell = async (over: Record<string, unknown>) =>
      (await mountAs(AccessLevel.CREATOR, [poll(over)])).find('[data-test="responses"]').text()

    it('shows responses with the in-area share', async () => {
      expect(await cell({ respondents: 40, inArea: 30 })).toBe('40 · 75% in area')
    })

    it('says the share is withheld when a group is too small', async () => {
      expect(await cell({ respondents: 12, inArea: null })).toBe('12 · in-area share withheld')
    })

    it('shows a dash for drafts', async () => {
      expect(await cell({ status: 'DRAFT' })).toBe('—')
    })

    it('links published polls to their results, not drafts', async () => {
      const w = await mountAs(AccessLevel.CREATOR, [
        poll({ id: 7, type: 'Election' }),
        poll({ id: 8, status: 'DRAFT' }),
      ])
      expect(w.find('a[href="/polls/election/7/results"]').text()).toBe('Results')
      expect(w.find('a[href="/polls/questionnaire/8/results"]').exists()).toBe(false)
    })

    it('says when a live poll closes', async () => {
      const inDays = (d: number) => new Date(Date.now() + d * 86_400_000 + 3_600_000).toISOString()
      const w = await mountAs(AccessLevel.CREATOR, [poll({ id: 1, closeDate: inDays(3) }), poll({ id: 2, closeDate: inDays(1) })])
      expect(w.text()).toContain('closes in 3 days')
      expect(w.text()).toContain('closes tomorrow')
    })

    it('the Emails box shows and switches a poll\'s milestone emails', async () => {
      vi.mocked(axios.put).mockResolvedValue({ data: { enabled: false } })
      const w = await mountAs(AccessLevel.CREATOR, [poll({ id: 7 }), poll({ id: 8, emails: false })])
      const boxes = w.findAll('[data-test="emails"]')
      expect(boxes.map(b => (b.element as HTMLInputElement).checked)).toEqual([true, false])

      await boxes[0].setValue(false)
      await flushPromises()
      expect(axios.put).toHaveBeenCalledWith('/api/creator/polls/questionnaire/7/emails', { enabled: false })
    })
  })

  describe('Reopen and History', () => {
    const poll = (over: Record<string, unknown>) => ({
      id: 7, type: 'Questionnaire', title: 'Vaccines', status: 'PUBLISHED', closeDate: null,
      createdAt: '2026-10-01T00:00:00Z', respondents: 0, inArea: 0, emails: true, ...over,
    })

    it('Reopen a published poll: confirms, reopens, then opens its edit page', async () => {
      vi.mocked(axios.post).mockResolvedValue({ data: {} })
      const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
      const w = await mountAs(AccessLevel.CREATOR, [poll({})])
      await w.find('[data-test="reopen"]').trigger('click')
      await flushPromises()
      expect(confirm).toHaveBeenCalledWith(expect.stringContaining('Vaccines'))
      expect(axios.post).toHaveBeenCalledWith('/api/creator/polls/questionnaire/7/reopen')
      expect(w.vm.$router.currentRoute.value.fullPath).toBe('/creator/polls/questionnaire/7/edit')
      confirm.mockRestore()
    })

    it('on a phone each poll is a card, with the same actions (Reopen works from it)', async () => {
      vi.mocked(axios.post).mockResolvedValue({ data: {} })
      const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
      const w = await mountAs(AccessLevel.CREATOR, [poll({}), poll({ id: 8, title: 'Parks', status: 'DRAFT' })])
      const cards = w.findAll('[data-test="poll-cards"] li')
      expect(cards.map(c => c.text())).toEqual([expect.stringContaining('Vaccines'), expect.stringContaining('Parks')])
      expect(cards[0].text()).toContain('History')
      expect(cards[1].text()).toContain('Edit')
      await cards[0].find('[data-test="card-reopen"]').trigger('click')
      await flushPromises()
      expect(axios.post).toHaveBeenCalledWith('/api/creator/polls/questionnaire/7/reopen')
      confirm.mockRestore()
    })

    it('no Reopen on a draft; every poll links to its history', async () => {
      const w = await mountAs(AccessLevel.CREATOR, [poll({ status: 'DRAFT' })])
      expect(w.find('[data-test="reopen"]').exists()).toBe(false)
      expect(w.find('[data-test="poll-history"]').attributes('href')).toBe('/history?type=questionnaire&id=7')
    })
  })

  describe('Show: columns, For Election and Enabled?', () => {
    afterEach(() => localStorage.clear())
    const poll = (over: Record<string, unknown>) => ({
      id: 7, type: 'BallotMeasure', title: 'Prop 1', status: 'PUBLISHED', closeDate: null,
      createdAt: '2026-10-01T00:00:00Z', respondents: 0, inArea: 0, emails: true,
      electionId: 93, electionTitle: 'November General Election', enabled: 'YES', ...over,
    })
    const headings = (w: Awaited<ReturnType<typeof mountAs>>) => w.findAll('th').map(th => th.text()).filter(Boolean)

    it('For Election and Enabled? follow Type, and a ballot measure links to its election', async () => {
      const w = await mountAs(AccessLevel.CREATOR, [poll({ enabled: 'PARTLY' })])
      expect(headings(w).slice(0, 5)).toEqual(['Title', 'Type', 'For Election', 'Enabled?', 'Status'])
      const link = w.find('[data-test="for-election"] a')
      expect(link.attributes('href')).toBe('/polls/election/93')
      expect(link.text()).toBe('November Genera...')
      expect(w.find('[data-test="enabled"]').text()).toBe('Partly')
    })

    it('other poll types show a dash for the election; switched off everywhere shows No', async () => {
      const w = await mountAs(AccessLevel.CREATOR, [poll({ type: 'Questionnaire', electionId: null, electionTitle: null, enabled: 'NO' })])
      expect(w.find('[data-test="for-election"]').text()).toBe('—')
      expect(w.find('[data-test="enabled"]').text()).toBe('No')
    })

    it('unticking a column in Show: hides it, and the choice is remembered', async () => {
      const w = await mountAs(AccessLevel.CREATOR, [poll({})])
      const options = w.find('[data-test="column-chooser"]').text()
      expect(options).toBe('Columns to showShow:TitleTypeFor ElectionEnabled?StatusClose dateResponsesEmails')
      await w.find('[data-test="show-election"]').setValue(false)
      expect(headings(w)).not.toContain('For Election')
      expect(w.find('[data-test="for-election"]').exists()).toBe(false)
      const again = await mountAs(AccessLevel.CREATOR, [poll({})])
      expect(headings(again)).not.toContain('For Election')
    })
  })
})

