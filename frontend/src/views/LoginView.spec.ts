import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { useAuthStore } from '@/stores/auth'
import LoginView from './LoginView.vue'

const { pushMock, replaceMock, routeQuery } = vi.hoisted(() => ({
  pushMock: vi.fn(),
  replaceMock: vi.fn(),
  routeQuery: { value: {} as Record<string, string> }
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: pushMock, replace: replaceMock }),
  useRoute: () => ({ query: routeQuery.value })
}))

// Each mount registers a `storage` listener on window; track wrappers so we can
// unmount them after every test and avoid stale listeners firing across tests.
const mounted: ReturnType<typeof mount>[] = []
function mountLogin(opts: { lastUserId?: number | null } = {}) {
  const pinia = createTestingPinia({ createSpy: vi.fn })
  // Who signed in last on this browser, as the page sees it when it opens.
  vi.mocked(useAuthStore(pinia).lastUserId).mockReturnValue(opts.lastUserId ?? null)
  const wrapper = mount(LoginView, {
    global: {
      plugins: [pinia],
      stubs: { 'router-link': true }
    }
  })
  mounted.push(wrapper)
  return wrapper
}

describe('LoginView (magic-link)', () => {
  beforeEach(() => {
    routeQuery.value = {}
    vi.clearAllMocks()
  })
  afterEach(() => {
    mounted.forEach((w) => w.unmount())
    mounted.length = 0
    vi.restoreAllMocks()
  })

  it('emails a link for an ACTIVE account and shows a confirmation', async () => {
    const wrapper = mountLogin()
    const auth = useAuthStore()
    vi.mocked(auth.accountStatus).mockResolvedValueOnce('ACTIVE')

    await wrapper.find('input[type="email"]').setValue('alice@test.local')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(auth.accountStatus).toHaveBeenCalledWith('alice@test.local')
    expect(auth.requestMagicLink).toHaveBeenCalledWith({ email: 'alice@test.local' })
    expect(wrapper.text()).toContain('Check your email')
    expect(wrapper.text()).toContain('alice@test.local')
  })

  it('routes an UNKNOWN email to register without emailing a link', async () => {
    const wrapper = mountLogin()
    const auth = useAuthStore()
    vi.mocked(auth.accountStatus).mockResolvedValueOnce('UNKNOWN')

    await wrapper.find('input[type="email"]').setValue('nobody@test.local')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(auth.requestMagicLink).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain("don't have an account for that email")
  })

  it('emails a link for a LAPSED account and notes the lapse', async () => {
    const wrapper = mountLogin()
    const auth = useAuthStore()
    vi.mocked(auth.accountStatus).mockResolvedValueOnce('LAPSED')

    await wrapper.find('input[type="email"]').setValue('lapsed@test.local')
    await wrapper.find('form').trigger('submit.prevent')
    await flushPromises()

    expect(auth.requestMagicLink).toHaveBeenCalledWith({ email: 'lapsed@test.local' })
    expect(wrapper.text()).toContain('Check your email')
    expect(wrapper.text()).toContain('membership has lapsed')
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

  describe('cross-tab sign-in', () => {
    it('adopts a token another tab wrote to localStorage and continues here', async () => {
      mountLogin()
      const auth = useAuthStore()

      window.dispatchEvent(new StorageEvent('storage', { key: 'token', newValue: 'cross-tab-jwt' }))
      await flushPromises()

      expect(auth.adoptToken).toHaveBeenCalledWith('cross-tab-jwt')
      expect(replaceMock).toHaveBeenCalledWith('/')
    })

    it('honours the ?redirect query when continuing', async () => {
      routeQuery.value = { redirect: '/polls/42' }
      mountLogin()
      const auth = useAuthStore()

      window.dispatchEvent(new StorageEvent('storage', { key: 'token', newValue: 'jwt' }))
      await flushPromises()

      expect(auth.adoptToken).toHaveBeenCalledWith('jwt')
      expect(replaceMock).toHaveBeenCalledWith('/polls/42')
    })

    it('moves on even when this tab was signed in as someone else', async () => {
      mountLogin()
      const auth = useAuthStore()
      ;(auth as any).token = 'old-user-jwt'
      ;(auth as any).isAuthenticated = true

      window.dispatchEvent(new StorageEvent('storage', { key: 'token', newValue: 'new-user-jwt' }))
      await flushPromises()

      expect(auth.adoptToken).toHaveBeenCalledWith('new-user-jwt')
      expect(replaceMock).toHaveBeenCalledWith('/')
    })

    it('ignores the login this tab already has', async () => {
      mountLogin()
      const auth = useAuthStore()
      ;(auth as any).token = 'same-jwt'

      window.dispatchEvent(new StorageEvent('storage', { key: 'token', newValue: 'same-jwt' }))
      await flushPromises()

      expect(auth.adoptToken).not.toHaveBeenCalled()
    })

    it('a different user than the last one goes home, not to the remembered page', async () => {
      routeQuery.value = { redirect: '/creator/polls/ballot-measure/79/edit' }
      mountLogin({ lastUserId: 5 })
      const auth = useAuthStore()
      ;(auth as any).user = { id: 9, profileComplete: true }

      window.dispatchEvent(new StorageEvent('storage', { key: 'token', newValue: 'jwt-user-9' }))
      await flushPromises()

      expect(replaceMock).toHaveBeenCalledWith('/')
    })

    it('the same user returning goes back to the remembered page', async () => {
      routeQuery.value = { redirect: '/creator/polls/ballot-measure/79/edit' }
      mountLogin({ lastUserId: 5 })
      const auth = useAuthStore()
      ;(auth as any).user = { id: 5, profileComplete: true }

      window.dispatchEvent(new StorageEvent('storage', { key: 'token', newValue: 'jwt-user-5' }))
      await flushPromises()

      expect(replaceMock).toHaveBeenCalledWith('/creator/polls/ballot-measure/79/edit')
    })

    it('ignores storage events that are not a fresh token', async () => {
      mountLogin()
      const auth = useAuthStore()

      window.dispatchEvent(new StorageEvent('storage', { key: 'theme', newValue: 'dark' }))
      window.dispatchEvent(new StorageEvent('storage', { key: 'token', newValue: null }))
      await flushPromises()

      expect(auth.adoptToken).not.toHaveBeenCalled()
      expect(replaceMock).not.toHaveBeenCalled()
    })
  })
})
