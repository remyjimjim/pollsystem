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
  it('a finished page shows without a draft banner', () => {
    params.page = 'terms'
    const w = mountView()
    expect(w.find('[data-test="legal-page"] h1').text()).toBe('Terms of Service')
    expect(w.find('[data-test="legal-draft"]').exists()).toBe(false)
  })

  it('no published page is a draft any more', () => {
    for (const page of ['terms', 'privacy', 'refunds']) {
      params.page = page
      expect(mountView().find('[data-test="legal-draft"]').exists()).toBe(false)
    }
  })

  it('an unknown page says so', () => {
    params.page = 'nope'
    expect(mountView().text()).toContain('Page not found')
  })
})
