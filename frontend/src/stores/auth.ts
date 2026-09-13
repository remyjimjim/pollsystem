import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import axios from 'axios'
import type { User, MagicLinkRequest, AuthResponse, CompleteProfileRequest } from '@/types'
import { AccessLevel } from '@/types'

/** Login routing decision from the backend; see AuthController.accountStatus. */
export type AccountStatus = 'UNKNOWN' | 'LAPSED' | 'ACTIVE'

/** Pay-first registration payload (all required; account created after payment). */
export interface RegisterCheckoutRequest {
  email: string
  phone: string
  zipcode: string
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

  // Can participate / see member CTAs: either currently paid, or CREATOR+ (who
  // are exempt from the subscription gate, granted access via other flows).
  // A logged-in account that is neither is a *lapsed* member (no free accounts).
  const isActiveMember = computed(() => isPaid.value || hasAccess(AccessLevel.CREATOR))

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
    localStorage.setItem('token', response.data.token)
    axios.defaults.headers.common['Authorization'] = `Bearer ${response.data.token}`
  }

  async function fetchUser(): Promise<void> {
    if (!token.value) return
    try {
      const response = await axios.get<User>('/api/auth/me')
      user.value = response.data
    } catch {
      logout()
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
    user.value = null
    token.value = null
    localStorage.removeItem('token')
    delete axios.defaults.headers.common['Authorization']
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
    fetchUser,
    completeProfile,
    logout
  }
})
