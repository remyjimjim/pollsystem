// @vitest-environment jsdom
// (DOMPurify mangles HTML under happy-dom; real browsers and jsdom are fine.)
import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import en from '@/i18n/en.json'
import LegalView from './LegalView.vue'

const params = { page: 'terms' }
vi.mock('vue-router', () => ({ useRoute: () => ({ params }) }))

const mountView = () => mount(LegalView, { global: { plugins: [createI18n({ legacy: false, locale: 'en', messages: { en } })] } })

describe('LegalView', () => {
  it('shows the page, and says it is still a draft while items are unfilled', () => {
    params.page = 'terms'
    const w = mountView()
    expect(w.find('[data-test="legal-page"] h1').text()).toBe('Terms of Service')
    expect(w.find('[data-test="legal-draft"]').text()).toMatch(/Draft: \d+ highlighted items? still to fill in/)
  })

  it('an unknown page says so', () => {
    params.page = 'nope'
    expect(mountView().text()).toContain('Page not found')
  })
})
