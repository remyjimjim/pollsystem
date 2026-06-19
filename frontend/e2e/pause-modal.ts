import type { Page } from '@playwright/test'

// Inject a visible HTML modal with a Close button and block the test until
// the user clicks Close. Playwright auto-dismisses native alert() calls,
// so a DOM overlay is the only way to gate progress on a human click that
// works deterministically under plain --headed (no --debug required).
export async function pauseWithModal(page: Page, title: string, body?: string) {
  await page.evaluate(([t, b]) => {
    const overlay = document.createElement('div')
    overlay.id = '__e2e_pause_overlay'
    overlay.style.cssText = [
      'position:fixed', 'inset:0', 'z-index:2147483647',
      'background:rgba(0,0,0,0.6)',
      'display:flex', 'align-items:center', 'justify-content:center',
    ].join(';')
    overlay.innerHTML = `
      <div style="background:#fff;padding:24px 28px;border-radius:8px;max-width:560px;
                  font-family:system-ui,sans-serif;box-shadow:0 10px 25px rgba(0,0,0,0.3);">
        <h2 style="margin:0 0 12px;font-size:18px;font-weight:600;color:#0f172a;">${t}</h2>
        ${b ? `<p style="margin:0 0 16px;font-size:13px;color:#475569;line-height:1.5;">${b}</p>` : ''}
        <button id="__e2e_pause_close"
                style="background:#0f172a;color:#fff;border:none;padding:8px 18px;
                       border-radius:4px;font-size:14px;font-weight:500;cursor:pointer;">
          Close
        </button>
      </div>
    `
    document.body.appendChild(overlay)
    ;(window as unknown as { __e2e_paused: boolean }).__e2e_paused = true
    document.getElementById('__e2e_pause_close')!.addEventListener('click', () => {
      overlay.remove()
      ;(window as unknown as { __e2e_paused: boolean }).__e2e_paused = false
    })
  }, [title, body ?? ''])

  await page.waitForFunction(
    () => !(window as unknown as { __e2e_paused: boolean }).__e2e_paused,
    undefined,
    { timeout: 30 * 60_000 }
  )
}
