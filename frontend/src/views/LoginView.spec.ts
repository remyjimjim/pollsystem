import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { useAuthStore } from '@/stores/auth'
import LoginView from './LoginView.vue'

const push = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({ push }),
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

describe('LoginView', () => {
  beforeEach(() => {
    push.mockClear()
  })
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('submits credentials to the auth store and redirects on success', async () => {
    const wrapper = mountLogin()
    const auth = useAuthStore()
    // createTestingPinia stubs actions; default mock returns undefined (resolves)

    await wrapper.find('input[type="email"]').setValue('alice@test.local')
    await wrapper.find('input[type="password"]').setValue('password123')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(auth.login).toHaveBeenCalledWith({
      email: 'alice@test.local',
      passcode: 'password123'
    })
    expect(push).toHaveBeenCalledWith('/')
  })

  it('redirects to the ?redirect query param when present', async () => {
    // Override the route mock for this test only
    const routerMock = await import('vue-router')
    vi.spyOn(routerMock, 'useRoute').mockReturnValue(
      { query: { redirect: '/creator/dashboard' } } as never
    )

    const wrapper = mountLogin()
    await wrapper.find('input[type="email"]').setValue('a@b')
    await wrapper.find('input[type="password"]').setValue('pw')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(push).toHaveBeenCalledWith('/creator/dashboard')
  })

  it('shows an error message when login throws', async () => {
    const wrapper = mountLogin()
    const auth = useAuthStore()
    vi.mocked(auth.login).mockRejectedValueOnce({
      response: { data: { message: 'Invalid email or password' } }
    })

    await wrapper.find('input[type="email"]').setValue('a@b')
    await wrapper.find('input[type="password"]').setValue('wrong')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Invalid email or password')
    expect(push).not.toHaveBeenCalled()
  })

  it('falls back to a generic message when the server response has no body', async () => {
    const wrapper = mountLogin()
    const auth = useAuthStore()
    vi.mocked(auth.login).mockRejectedValueOnce(new Error('network'))

    await wrapper.find('input[type="email"]').setValue('a@b')
    await wrapper.find('input[type="password"]').setValue('pw')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(wrapper.text()).toContain('Invalid email or password')
  })
})
