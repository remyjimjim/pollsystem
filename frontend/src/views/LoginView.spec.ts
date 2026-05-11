import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { useAuthStore } from '@/stores/auth'
import LoginView from './LoginView.vue'

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ query: {} })
}))

function mountLogin() {
  return mount(LoginView, {
    global: {
      plugins: [createTestingPinia({ createSpy: vi.fn })],
      stubs: { 'router-link': true }
    }
  })
}

describe('LoginView (magic-link)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('calls requestMagicLink with the entered email and shows a confirmation', async () => {
    const wrapper = mountLogin()
    const auth = useAuthStore()
    // createTestingPinia stubs actions; default returns undefined (resolves)

    await wrapper.find('input[type="email"]').setValue('alice@test.local')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(auth.requestMagicLink).toHaveBeenCalledWith({ email: 'alice@test.local' })
    expect(wrapper.text()).toContain('Check your email')
    expect(wrapper.text()).toContain('alice@test.local')
  })

  it('shows a "no account" message when the backend returns 400', async () => {
    const wrapper = mountLogin()
    const auth = useAuthStore()
    vi.mocked(auth.requestMagicLink).mockRejectedValueOnce({ response: { status: 400 } })

    await wrapper.find('input[type="email"]').setValue('nobody@test.local')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain("don't have an account for that email")
  })

  it('falls back to a generic message on other errors', async () => {
    const wrapper = mountLogin()
    const auth = useAuthStore()
    vi.mocked(auth.requestMagicLink).mockRejectedValueOnce(new Error('network'))

    await wrapper.find('input[type="email"]').setValue('a@b')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Could not send the sign-in link')
  })
})
