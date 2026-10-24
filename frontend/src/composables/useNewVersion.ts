import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'

/**
 * Whether a newer version of the site has been deployed since this tab loaded
 * it. A single-page app keeps running the code it first loaded, so without
 * this an open tab (often on a phone) stays on the old version indefinitely.
 * Checked when the tab comes back into view, on navigation, and every few
 * minutes. Off on the dev server, which updates itself.
 */
export function useNewVersion(everyMs = 5 * 60 * 1000) {
  const available = ref(false)
  let timer: ReturnType<typeof setInterval> | undefined

  async function check() {
    if (import.meta.env.DEV || available.value) return
    try {
      const r = await fetch(`/version.json?t=${Date.now()}`, { cache: 'no-store' })
      if (!r.ok) return
      const { build } = await r.json()
      if (build && build !== __BUILD_ID__) available.value = true
    } catch { /* offline or blocked: try again later */ }
  }
  const onVisible = () => { if (document.visibilityState === 'visible') void check() }

  const router = useRouter()
  const stopNav = router.afterEach(() => { void check() })
  onMounted(() => {
    timer = setInterval(check, everyMs)
    document.addEventListener('visibilitychange', onVisible)
  })
  onUnmounted(() => {
    clearInterval(timer)
    document.removeEventListener('visibilitychange', onVisible)
    stopNav()
  })

  return { available, reload: () => window.location.reload() }
}
