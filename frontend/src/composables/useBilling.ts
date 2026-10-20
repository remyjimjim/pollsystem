import { ref } from 'vue'
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import type { User } from '@/types'

/**
 * Drives the Stripe purchase / management flows. Both backend endpoints return
 * `{ url }` for a Stripe-hosted page (Checkout or the Customer Portal); we send
 * the browser there. On success the page navigates away, so `busy` stays true
 * until unload; on failure we surface an error and reset.
 */
export function useBilling() {
  const busy = ref(false)
  const error = ref<string | null>(null)

  async function redirectTo(endpoint: string): Promise<void> {
    busy.value = true
    error.value = null
    try {
      const res = await axios.post<{ url: string }>(endpoint)
      window.location.href = res.data.url
    } catch {
      error.value = 'unavailable'
      busy.value = false
    }
  }

  const startCheckout = () => redirectTo('/api/billing/checkout')
  const openPortal = () => redirectTo('/api/billing/portal')

  // Cancel (ends with the paid month) or keep the membership; stays on the page.
  async function setCancelling(cancel: boolean): Promise<boolean> {
    busy.value = true
    error.value = null
    try {
      const res = await axios.post<User>(cancel ? '/api/billing/cancel' : '/api/billing/resume')
      useAuthStore().user = res.data
      return true
    } catch {
      error.value = 'unavailable'
      return false
    } finally {
      busy.value = false
    }
  }
  const cancelMembership = () => setCancelling(true)
  const keepMembership = () => setCancelling(false)

  return { busy, error, startCheckout, openPortal, cancelMembership, keepMembership }
}
