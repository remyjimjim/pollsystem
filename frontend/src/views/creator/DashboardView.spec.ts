import { describe, expect, it, vi } from 'vitest'
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
      closeDate: null, createdAt: '2026-10-01T00:00:00Z', respondents: 0, inArea: 0, ...over,
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
  })
})
