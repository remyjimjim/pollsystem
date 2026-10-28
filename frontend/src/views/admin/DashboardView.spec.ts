import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'
import axios from 'axios'
import en from '@/i18n/en.json'
import DashboardView from './DashboardView.vue'

vi.mock('axios')

const DATA = {
  areas: ['California'],
  pendingAssignedToMe: [],
  unassignedInScope: [{
    id: 61, userEmail: 'newbie@test.local', regionLabel: 'California', pollTypeIds: [1, 2],
    status: 'PENDING', submittedAt: '2026-10-01T00:00:00Z', assignedAdminId: null,
  }],
  staleCount: 1,
  creatorsInScopeCount: 3,
  recentDecisions: [{ requestId: 57, userEmail: 'old@test.local', regionLabel: 'California', status: 'APPROVED', processedAt: '2026-10-16T00:00:00Z' }],
}

async function mountView() {
  setActivePinia(createPinia())
  vi.mocked(axios.get).mockResolvedValue({ data: DATA })
  vi.mocked(axios.post).mockResolvedValue({ data: {} })
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: '/:p(.*)*', component: { template: '<div/>' } }] })
  const i18n = createI18n({ legacy: false, locale: 'en', messages: { en } })
  const w = mount(DashboardView, { global: { plugins: [router, i18n] } })
  await flushPromises()
  return w
}

afterEach(() => vi.resetAllMocks())

describe('Admin DashboardView: cards on phones (2026-10-28)', () => {
  it('a pending request is a card with its badges, and Approve works from it', async () => {
    const w = await mountView()
    const card = w.find('[data-test="pending-cards"] li')
    expect(card.text()).toContain('#61')
    expect(card.text()).toContain('newbie@test.local')
    expect(card.text()).toContain('California')
    expect(card.text()).toContain(en.admin.dashboard.badgeStale)
    expect(card.text()).toContain(en.admin.dashboard.badgeUnassigned)
    await card.findAll('button').find(b => b.text() === en.admin.dashboard.approve)!.trigger('click')
    await flushPromises()
    expect(axios.post).toHaveBeenCalledWith('/api/admin/creator-requests/batch-approve', { requestIds: [61] })
  })

  it('a recent decision is a card with its status, request and member', async () => {
    const w = await mountView()
    const card = w.find('[data-test="decision-cards"] li')
    expect(card.text()).toContain('APPROVED')
    expect(card.text()).toContain('#57')
    expect(card.text()).toContain('old@test.local')
  })
})
