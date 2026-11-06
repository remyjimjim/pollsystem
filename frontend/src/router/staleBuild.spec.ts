import { describe, expect, it, vi } from 'vitest'

vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({}) }))
const { isStaleBuildError } = await import('./index')

describe('isStaleBuildError (2026-11-06)', () => {
  it('recognises a page file that failed to load after a deploy, in each browser', () => {
    expect(isStaleBuildError(new TypeError('Failed to fetch dynamically imported module: https://x/assets/HistoryView-abc.js'))).toBe(true) // Chrome
    expect(isStaleBuildError(new TypeError('error loading dynamically imported module'))).toBe(true) // Firefox
    expect(isStaleBuildError(new TypeError('Importing a module script failed.'))).toBe(true) // Safari
    expect(isStaleBuildError(new Error('Unable to preload CSS for /assets/HistoryView-abc.css'))).toBe(true)
  })
  it('leaves other navigation errors alone', () => {
    expect(isStaleBuildError(new Error('Request failed with status code 500'))).toBe(false)
    expect(isStaleBuildError('Navigation cancelled')).toBe(false)
  })
})
