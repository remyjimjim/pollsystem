import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import axios from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import en from '@/i18n/en.json'
import { useAuthStore } from '@/stores/auth'
import { AccessLevel } from '@/types'
import BillingBanner from './BillingBanner.vue'

vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
vi.mock('axios')

function mountAs(access: AccessLevel, paidUntil: string | null, cancelAtPeriodEnd = false) {
  setActivePinia(createPinia())
  const auth = useAuthStore()
  auth.token = 't'
  auth.user = { id: 1, email: 'a@b.c', access, paidUntil, cancelAtPeriodEnd, profileComplete: true } as never
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

  it('cancelling asks first, then shows when the membership ends, with a way to keep it', async () => {
    const w = mountAs(AccessLevel.USER, future)
    vi.mocked(axios.post).mockResolvedValue({ data: { id: 1, email: 'a@b.c', access: AccessLevel.USER, paidUntil: future, cancelAtPeriodEnd: true, profileComplete: true } })
    await w.find('[data-test="billing-cancel"]').trigger('click')
    expect(w.find('[data-test="billing-cancel-confirm"]').text()).toContain('stays active until')
    expect(axios.post).not.toHaveBeenCalled()

    await w.find('[data-test="billing-cancel-yes"]').trigger('click')
    await flushPromises()
    expect(axios.post).toHaveBeenCalledWith('/api/billing/cancel')
    expect(w.find('[data-test="billing-ending"]').text()).toMatch(/^Your membership ends on .+\. Until then/)
    expect(w.find('[data-test="billing-keep"]').exists()).toBe(true)
  })

  it('a cancelled member can keep their membership', async () => {
    const w = mountAs(AccessLevel.USER, future, true)
    vi.mocked(axios.post).mockResolvedValue({ data: { id: 1, email: 'a@b.c', access: AccessLevel.USER, paidUntil: future, cancelAtPeriodEnd: false, profileComplete: true } })
    expect(w.text()).toContain('Your membership is ending')
    await w.find('[data-test="billing-keep"]').trigger('click')
    await flushPromises()
    expect(axios.post).toHaveBeenCalledWith('/api/billing/resume')
    expect(w.text()).toContain('Cancel membership')
  })
})
