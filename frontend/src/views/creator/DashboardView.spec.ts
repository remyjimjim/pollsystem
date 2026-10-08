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

async function mountAs(access: AccessLevel) {
  vi.mocked(axios.get).mockResolvedValue({ data: [] })
  setActivePinia(createPinia())
  const auth = useAuthStore()
  auth.token = 't'
  auth.user = { id: 1, email: 'c@d.e', access, profileComplete: true } as never
  const stub = { template: '<div/>' }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: ['/', '/creator/request', '/admin-request', '/creator/polls/new'].map(path => ({ path, component: stub })),
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
})
