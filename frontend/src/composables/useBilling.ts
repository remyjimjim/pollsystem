import { ref } from 'vue'
import axios from 'axios'

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

  return { busy, error, startCheckout, openPortal }
}
