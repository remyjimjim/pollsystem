import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import en from '@/i18n/en.json'
import { useAuthStore } from '@/stores/auth'
import { AccessLevel } from '@/types'
import BillingBanner from './BillingBanner.vue'

vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))

function mountAs(access: AccessLevel, paidUntil: string | null) {
  setActivePinia(createPinia())
  const auth = useAuthStore()
  auth.token = 't'
  auth.user = { id: 1, email: 'a@b.c', access, paidUntil, profileComplete: true } as never
  const i18n = createI18n({ legacy: false, locale: 'en', messages: { en } })
  return mount(BillingBanner, { global: { plugins: [i18n] } })
}

const future = new Date(Date.now() + 86_400_000).toISOString()

describe('BillingBanner', () => {
  beforeEach(() => localStorage.clear())

  it('a paid member can manage their subscription', () => {
    expect(mountAs(AccessLevel.USER, future).text()).toContain('Manage subscription')
  })

  it('an unpaid creator or admin is asked to renew (they pay too)', () => {
    expect(mountAs(AccessLevel.CREATOR, null).text()).toContain('Your membership has lapsed')
    expect(mountAs(AccessLevel.ADMIN, null).text()).toContain('Your membership has lapsed')
  })

  it('an unpaid super (exempt) sees no banner at all', () => {
    expect(mountAs(AccessLevel.SUPER, null).html()).not.toContain('<div')
  })
})
