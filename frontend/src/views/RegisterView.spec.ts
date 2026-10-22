import { describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { useAuthStore } from '@/stores/auth'
import RegisterView from './RegisterView.vue'

function mountView() {
  return mount(RegisterView, {
    global: { plugins: [createTestingPinia({ createSpy: vi.fn })], stubs: { 'router-link': true, 'i18n-t': true } }
  })
}

async function fill(w: ReturnType<typeof mountView>, email: string, phone: string, zip: string) {
  await w.find('input[type="email"]').setValue(email)
  await w.find('input[type="tel"]').setValue(phone)
  await w.find('input[autocomplete="postal-code"]').setValue(zip)
  await w.find('form').trigger('submit.prevent')
  await flushPromises()
}

describe('RegisterView: tidying what was typed', () => {
  it('autofilled spaces and a pasted non-breaking hyphen are cleaned before checkout', async () => {
    const w = mountView()
    const auth = useAuthStore()
    vi.mocked(auth.registerCheckout).mockRejectedValue({ response: { status: 503 } })   // stay on the page
    await fill(w, ' pat@example.test ', '(401) 992‑4938', '98264 ')
    expect(auth.registerCheckout).toHaveBeenCalledWith({ email: 'pat@example.test', phone: '(401) 992-4938', zipcode: '98264' })
  })

  it('a phone that is not a phone number is reported before checkout', async () => {
    const w = mountView()
    const auth = useAuthStore()
    await fill(w, 'pat@example.test', 'call me', '98264')
    expect(auth.registerCheckout).not.toHaveBeenCalled()
    expect(w.text()).toContain('Please check your phone number')
  })
})
