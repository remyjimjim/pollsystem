import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import axios from 'axios'
import { useAuthStore } from './auth'
import { AccessLevel, type User } from '@/types'

vi.mock('axios')
const mockedAxios = vi.mocked(axios, true)

function makeUser(access: AccessLevel = AccessLevel.USER): User {
  return {
    id: 1,
    email: 'alice@test.local',
    phone: '+15551234567',
    zipcode: '90001',
    access,
    isEnabled: true,
    paidUntil: null,
    profileComplete: true
  }
}

describe('useAuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    // Reset axios defaults the store may set
    delete mockedAxios.defaults.headers.common['Authorization']
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('isAuthenticated', () => {
    it('is false with no token and no user', () => {
      const auth = useAuthStore()
      expect(auth.isAuthenticated).toBe(false)
    })

    it('is true after a successful magic-link redeem', async () => {
      mockedAxios.post.mockResolvedValueOnce({
        data: { token: 'tok', user: makeUser() }
      })

      const auth = useAuthStore()
      await auth.redeemMagicLink('raw-token')

      expect(auth.isAuthenticated).toBe(true)
      expect(auth.user?.email).toBe('alice@test.local')
      expect(auth.token).toBe('tok')
    })

    it('adopts a token from another tab and loads the user (cross-tab sign-in)', async () => {
      // adoptToken sets the header + calls fetchUser (GET /api/auth/me).
      mockedAxios.get.mockResolvedValueOnce({ data: makeUser() })

      const auth = useAuthStore()
      await auth.adoptToken('cross-tab-tok')

      expect(auth.token).toBe('cross-tab-tok')
      expect(auth.isAuthenticated).toBe(true)
      expect(auth.user?.email).toBe('alice@test.local')
      expect(mockedAxios.defaults.headers.common['Authorization']).toBe('Bearer cross-tab-tok')
    })
  })

  describe('hasAccess', () => {
    it('returns false when no user is loaded', () => {
      const auth = useAuthStore()
      expect(auth.hasAccess(AccessLevel.USER)).toBe(false)
    })

    it.each([
      [AccessLevel.VIEWER, AccessLevel.USER, false],
      [AccessLevel.USER, AccessLevel.USER, true],
      [AccessLevel.USER, AccessLevel.CREATOR, false],
      [AccessLevel.CREATOR, AccessLevel.USER, true],
      [AccessLevel.ADMIN, AccessLevel.CREATOR, true],
      [AccessLevel.SUPER, AccessLevel.ADMIN, true],
      [AccessLevel.SUPER, AccessLevel.SUPER, true]
    ])('user %s vs required %s → %s', async (userLevel, required, expected) => {
      mockedAxios.post.mockResolvedValueOnce({
        data: { token: 'tok', user: makeUser(userLevel) }
      })
      const auth = useAuthStore()
      await auth.redeemMagicLink('raw')
      expect(auth.hasAccess(required)).toBe(expected)
    })
  })

  describe('isActiveMember', () => {
    const future = new Date(Date.now() + 86_400_000).toISOString()
    const past = new Date(Date.now() - 86_400_000).toISOString()

    // Creators and admins pay like everyone else; only SUPER is exempt
    // (the backend's ParticipationGuard).
    it.each([
      [AccessLevel.USER, future, true],
      [AccessLevel.USER, null, false],
      [AccessLevel.USER, past, false],
      [AccessLevel.CREATOR, future, true],
      [AccessLevel.CREATOR, null, false],
      [AccessLevel.ADMIN, null, false],
      [AccessLevel.SUPER, null, true]
    ])('%s with paidUntil=%s → %s', async (access, paidUntil, expected) => {
      mockedAxios.post.mockResolvedValueOnce({
        data: { token: 'tok', user: { ...makeUser(access), paidUntil } }
      })
      const auth = useAuthStore()
      await auth.redeemMagicLink('raw')
      expect(auth.isActiveMember).toBe(expected)
    })
  })

  describe('requestMagicLink', () => {
    it('POSTs to the magic-link request endpoint', async () => {
      mockedAxios.post.mockResolvedValueOnce({ status: 202, data: {} })
      const auth = useAuthStore()
      await auth.requestMagicLink({
        email: 'alice@test.local',
        phone: '+15551234567',
        zipcode: '90001'
      })
      expect(mockedAxios.post).toHaveBeenCalledWith(
        '/api/auth/magic-link/request',
        { email: 'alice@test.local', phone: '+15551234567', zipcode: '90001' }
      )
      // Issuing a link does not authenticate the user.
      expect(auth.isAuthenticated).toBe(false)
      expect(localStorage.getItem('token')).toBeNull()
    })

    it('propagates server errors', async () => {
      mockedAxios.post.mockRejectedValueOnce(new Error('409'))
      const auth = useAuthStore()
      await expect(
        auth.requestMagicLink({ email: 'a@b' })
      ).rejects.toThrow()
    })
  })

  describe('redeemMagicLink', () => {
    it('persists token to localStorage and sets the Authorization header', async () => {
      mockedAxios.post.mockResolvedValueOnce({
        data: { token: 'tok-123', user: makeUser() }
      })

      const auth = useAuthStore()
      await auth.redeemMagicLink('raw-token')

      expect(mockedAxios.post).toHaveBeenCalledWith(
        '/api/auth/magic-link/redeem',
        { token: 'raw-token' }
      )
      expect(localStorage.getItem('token')).toBe('tok-123')
      expect(mockedAxios.defaults.headers.common['Authorization']).toBe('Bearer tok-123')
    })

    it('propagates server errors and leaves state unauthenticated', async () => {
      mockedAxios.post.mockRejectedValueOnce(new Error('401'))
      const auth = useAuthStore()
      await expect(auth.redeemMagicLink('expired')).rejects.toThrow()
      expect(auth.isAuthenticated).toBe(false)
      expect(localStorage.getItem('token')).toBeNull()
    })
  })

  describe('logout', () => {
    it('clears state and storage', async () => {
      mockedAxios.post.mockResolvedValueOnce({
        data: { token: 'tok', user: makeUser() }
      })
      const auth = useAuthStore()
      await auth.redeemMagicLink('raw')
      expect(auth.isAuthenticated).toBe(true)

      auth.logout()
      expect(auth.isAuthenticated).toBe(false)
      expect(auth.token).toBeNull()
      expect(auth.user).toBeNull()
      expect(localStorage.getItem('token')).toBeNull()
      expect(mockedAxios.defaults.headers.common['Authorization']).toBeUndefined()
    })
  })

  describe('fetchUser', () => {
    it('does nothing when no token is set', async () => {
      const auth = useAuthStore()
      await auth.fetchUser()
      expect(mockedAxios.get).not.toHaveBeenCalled()
    })

    it('populates user on success', async () => {
      // Seed a token (e.g. from a previous session)
      localStorage.setItem('token', 'persisted')
      const auth = useAuthStore()
      mockedAxios.get.mockResolvedValueOnce({ data: makeUser() })

      await auth.fetchUser()
      expect(auth.user?.email).toBe('alice@test.local')
    })

    it('logs out on rejection', async () => {
      localStorage.setItem('token', 'expired')
      const auth = useAuthStore()
      mockedAxios.get.mockRejectedValueOnce(new Error('401'))

      await auth.fetchUser()
      expect(auth.user).toBeNull()
      expect(auth.token).toBeNull()
      expect(localStorage.getItem('token')).toBeNull()
    })
  })
})
