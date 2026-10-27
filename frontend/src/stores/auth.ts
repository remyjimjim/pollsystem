import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import axios from 'axios'
import type { User, MagicLinkRequest, AuthResponse, CompleteProfileRequest } from '@/types'
import { AccessLevel } from '@/types'

/** Login routing decision from the backend; see AuthController.accountStatus. */
export type AccountStatus = 'UNKNOWN' | 'LAPSED' | 'ACTIVE' | 'DEMO'

/** Pay-first registration payload (all required; account created after payment). */
export interface RegisterCheckoutRequest {
  email: string
  phone: string
  zipcode: string
  /** "I'm 13 or older" ticked (Terms: minimum age 13). */
  ageConfirmed: boolean
}

export const useAuthStore = defineStore('auth', () => {
  const user = ref<User | null>(null)
  const token = ref<string | null>(localStorage.getItem('token'))

  const isAuthenticated = computed(() => !!token.value && !!user.value)

  const hasAccess = (level: AccessLevel): boolean => {
    if (!user.value) return false
    const hierarchy: Record<string, number> = {
      VIEWER: 0, USER: 1, CREATOR: 2, ADMIN: 3, SUPER: 4
    }
    return hierarchy[user.value.access] >= hierarchy[level]
  }

  // A live paid membership: paidUntil set and still in the future.
  const isPaid = computed(() => {
    const until = user.value?.paidUntil
    return !!until && new Date(until).getTime() > Date.now()
  })

  // Can participate / see member CTAs: currently paid. Creators and admins pay
  // too; only SUPER is exempt (mirrors the backend's ParticipationGuard). A
  // logged-in account that is neither is a *lapsed* member (no free accounts).
  const isActiveMember = computed(() => isPaid.value || hasAccess(AccessLevel.SUPER))

  // Set auth header for all requests
  if (token.value) {
    axios.defaults.headers.common['Authorization'] = `Bearer ${token.value}`
  }

  /**
   * Request a magic-link sign-in. Backend creates the user if email is new
   * (formatting-validated phone + zipcode) and emails a one-shot token.
   * Returns 202 regardless of whether the email pre-existed.
   */
  async function requestMagicLink(data: MagicLinkRequest): Promise<void> {
    await axios.post('/api/auth/magic-link/request', data)
  }

  /**
   * Ask the backend how to route a login attempt: UNKNOWN (send to /register),
   * LAPSED (email a link, they renew after signing in), or ACTIVE (email a link).
   */
  async function accountStatus(email: string): Promise<AccountStatus> {
    const res = await axios.post<{ status: AccountStatus }>('/api/auth/status', { email })
    return res.data.status
  }

  /**
   * Pay-first registration: validate + start Stripe Checkout. Returns the
   * hosted-checkout URL to redirect to; the account is created by the webhook
   * once payment succeeds. Throws (409/400) if email/phone are taken or the
   * zipcode is unknown.
   */
  async function registerCheckout(data: RegisterCheckoutRequest): Promise<string> {
    const res = await axios.post<{ url: string }>('/api/auth/register-checkout', data)
    return res.data.url
  }

  /**
   * Redeem the token from the magic-link URL. On success, store the JWT and
   * load the user; subsequent /api calls are authenticated.
   */
  async function redeemMagicLink(rawToken: string): Promise<void> {
    const response = await axios.post<AuthResponse>(
      '/api/auth/magic-link/redeem',
      { token: rawToken }
    )
    token.value = response.data.token
    user.value = response.data.user
    rememberUser(response.data.user.id)
    localStorage.setItem('token', response.data.token)
    axios.defaults.headers.common['Authorization'] = `Bearer ${response.data.token}`
  }

  /** Sign in with the 6-digit code from a sign-in email (for phones; 2026-10-27). */
  async function redeemCode(email: string, code: string): Promise<void> {
    const response = await axios.post<AuthResponse>('/api/auth/magic-link/code', { email, code })
    token.value = response.data.token
    user.value = response.data.user
    rememberUser(response.data.user.id)
    localStorage.setItem('token', response.data.token)
    axios.defaults.headers.common['Authorization'] = `Bearer ${response.data.token}`
  }

  /** The demo address while the demo is on: straight in, no email (V42). */
  async function demoSignIn(email: string): Promise<void> {
    const response = await axios.post<AuthResponse>('/api/auth/demo-sign-in', { email })
    token.value = response.data.token
    user.value = response.data.user
    rememberUser(response.data.user.id)
    localStorage.setItem('token', response.data.token)
    axios.defaults.headers.common['Authorization'] = `Bearer ${response.data.token}`
  }

  /**
   * Adopt a JWT another tab wrote to localStorage (cross-tab sign-in). The
   * magic-link tab redeems and calls localStorage.setItem('token', …), which
   * fires a `storage` event in every other tab on this origin; the login tab
   * calls this to sync its in-memory session + auth header and load the user,
   * so it signs itself in without the user leaving that tab.
   */
  /**
   * Sign in with a token obtained another way (the local-only Quick sign-in):
   * like redeeming a link, it's stored for every tab, then loaded here.
   */
  async function signInWithToken(newToken: string): Promise<void> {
    localStorage.setItem('token', newToken)
    await adoptToken(newToken)
  }

  async function adoptToken(newToken: string): Promise<void> {
    token.value = newToken
    axios.defaults.headers.common['Authorization'] = `Bearer ${newToken}`
    await fetchUser()
  }

  async function fetchUser(): Promise<void> {
    if (!token.value) return
    try {
      const response = await axios.get<User>('/api/auth/me')
      user.value = response.data
      rememberUser(response.data.id)
    } catch (e: any) {
      // Log out only when the server says this login is no good (401/403).
      // If it's unreachable (a restart, a network blip) or erring, keep the
      // login: it works again once the server is back. Logging out here would
      // also log out every other tab.
      const status = e?.response?.status
      if (status === 401 || status === 403) logout()
    }
  }

  // Who last signed in on this browser (kept after logout), so the Login page
  // only takes a returning user back to the page they were on, never someone
  // else (that page may be another user's, e.g. their draft).
  const LAST_USER_KEY = 'lastUserId'
  function rememberUser(id: number): void {
    try { localStorage.setItem(LAST_USER_KEY, String(id)) } catch { /* storage unavailable: no memory */ }
  }
  function lastUserId(): number | null {
    try {
      const v = localStorage.getItem(LAST_USER_KEY)
      return v ? Number(v) : null
    } catch {
      return null
    }
  }

  /**
   * Supply the phone + zipcode a payment-first user was provisioned without.
   * On success the returned (now-complete) user replaces the store's, so the
   * router guard lets them participate.
   */
  async function completeProfile(data: CompleteProfileRequest): Promise<void> {
    const response = await axios.post<User>('/api/auth/complete-profile', data)
    user.value = response.data
  }

  function logout(): void {
    clearSession()
    localStorage.removeItem('token')
  }

  /** Forget the session in this tab only (the shared storage is already cleared). */
  function clearSession(): void {
    user.value = null
    token.value = null
    delete axios.defaults.headers.common['Authorization']
  }

  /**
   * Keep this tab in step with the others: the login lives in localStorage,
   * shared by every tab, but each tab also holds it in memory. Called on a
   * `storage` event. Returns what changed:
   * - 'signedOut': another tab logged out, so this one is logged out too
   *   (it must not stay usable, e.g. on a shared computer);
   * - 'signedIn': this tab wasn't signed in and another tab signed in (e.g.
   *   the emailed link opened in a new tab); the caller adopts the login;
   * - 'switched': another tab replaced this tab's login with a different one;
   *   the caller reloads so no page keeps showing the previous user's data;
   * - null: nothing to do (another key, or the login this tab already has,
   *   e.g. adopted by the Login page's own cross-tab sign-in).
   */
  function syncFromOtherTab(e: Pick<StorageEvent, 'key' | 'newValue'>): 'signedOut' | 'signedIn' | 'switched' | null {
    // key null = the whole storage was cleared.
    if (e.key !== 'token' && e.key !== null) return null
    const next = e.key === null ? null : e.newValue
    if (next === token.value) return null
    if (!next) {
      if (!token.value) return null
      clearSession()
      return 'signedOut'
    }
    return token.value ? 'switched' : 'signedIn'
  }

  return {
    user,
    token,
    isAuthenticated,
    hasAccess,
    isPaid,
    isActiveMember,
    requestMagicLink,
    accountStatus,
    registerCheckout,
    redeemMagicLink,
    redeemCode,
    demoSignIn,
    adoptToken,
    fetchUser,
    completeProfile,
    logout,
    syncFromOtherTab,
    lastUserId,
    signInWithToken
  }
})
