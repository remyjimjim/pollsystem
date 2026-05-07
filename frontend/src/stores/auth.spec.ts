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
    isEnabled: true
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

    it('is true after a successful login', async () => {
      mockedAxios.post.mockResolvedValueOnce({
        data: { token: 'tok', user: makeUser() }
      })

      const auth = useAuthStore()
      await auth.login({ email: 'alice@test.local', passcode: 'password123' })

      expect(auth.isAuthenticated).toBe(true)
      expect(auth.user?.email).toBe('alice@test.local')
      expect(auth.token).toBe('tok')
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
      await auth.login({ email: 'x@x', passcode: 'pw' })
      expect(auth.hasAccess(required)).toBe(expected)
    })
  })

  describe('login', () => {
    it('persists token to localStorage and sets the Authorization header', async () => {
      mockedAxios.post.mockResolvedValueOnce({
        data: { token: 'tok-123', user: makeUser() }
      })

      const auth = useAuthStore()
      await auth.login({ email: 'a@b', passcode: 'pw' })

      expect(localStorage.getItem('token')).toBe('tok-123')
      expect(mockedAxios.defaults.headers.common['Authorization']).toBe('Bearer tok-123')
    })

    it('propagates server errors', async () => {
      mockedAxios.post.mockRejectedValueOnce(new Error('401'))
      const auth = useAuthStore()
      await expect(auth.login({ email: 'a@b', passcode: 'wrong' })).rejects.toThrow()
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
      await auth.login({ email: 'a@b', passcode: 'pw' })
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
