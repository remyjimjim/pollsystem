// @vitest-environment jsdom
// (DOMPurify mangles HTML under happy-dom; real browsers and jsdom are fine.)
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'
import en from '@/i18n/en.json'
import nb from '@/i18n/nb.json'
import { useAuthStore } from '@/stores/auth'
import { AccessLevel } from '@/types'
import HelpView from './HelpView.vue'

vi.mock('@/help/manual', async (orig) => {
  const real = await orig<typeof import('@/help/manual')>()
  const page = (section: string, slug: string, title: string) =>
    real.parsePage(section as never, slug, `# ${title}\n\nBody of ${title}. See [other](../viewer/finding.md).`)
  return {
    ...real,
    PAGES: [
      page('viewer', 'finding', 'Finding'),
      page('user', 'answering', 'Answering'),
      page('admin', 'approving', 'Approving'),
    ],
    findPage: (s: string, slug: string) =>
      [page('viewer', 'finding', 'Finding'), page('user', 'answering', 'Answering'), page('admin', 'approving', 'Approving')]
        .find(p => p.section === s && p.slug === slug),
  }
})

async function mountAt(path: string, access: AccessLevel | null, locale = 'en') {
  setActivePinia(createPinia())
  const auth = useAuthStore()
  if (access) {
    auth.token = 't'
    auth.user = { email: 'a@b.c', access, profileComplete: true } as never
  }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/help', component: HelpView },
      { path: '/help/:section/:slug', component: HelpView },
      { path: '/login', name: 'Login', component: { template: '<div/>' } },
    ],
  })
  router.push(path)
  await router.isReady()
  const i18n = createI18n({ legacy: false, locale, fallbackLocale: 'en', messages: { en, nb } })
  const w = mount(HelpView, { global: { plugins: [router, i18n] } })
  await flushPromises()
  return { w, router }
}

describe('HelpView', () => {
  beforeEach(() => localStorage.clear())

  it('index: anonymous visitors see only the viewer pages', async () => {
    const { w } = await mountAt('/help', null)
    expect(w.text()).toContain('Finding')
    expect(w.text()).not.toContain('Answering')
    expect(w.text()).not.toContain('Approving')
  })

  it('index: a member sees viewer + member pages, not admin ones', async () => {
    const { w } = await mountAt('/help', AccessLevel.USER)
    expect(w.text()).toContain('Members')
    expect(w.text()).toContain('Answering')
    expect(w.text()).not.toContain('Approving')
  })

  it('page: renders the Markdown, and in-page links stay in the app', async () => {
    const { w, router } = await mountAt('/help/user/answering', AccessLevel.ADMIN)
    expect(w.find('article h1').text()).toBe('Answering')
    const link = w.find('article a')
    expect(link.attributes('href')).toBe('/help/viewer/finding')
    await link.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.fullPath).toBe('/help/viewer/finding')
  })

  it('page: a signed-out visitor is asked to sign in for member pages', async () => {
    const { w } = await mountAt('/help/user/answering', null)
    expect(w.find('article').exists()).toBe(false)
    expect(w.text()).toContain('signed-in members')
  })

  it('page: unknown or out-of-reach pages say so', async () => {
    expect((await mountAt('/help/viewer/nope', AccessLevel.USER)).w.text()).toContain('no help page here')
    expect((await mountAt('/help/admin/approving', AccessLevel.USER)).w.text()).toContain('no help page here')
  })

  it('shows the English-only note in other languages', async () => {
    expect((await mountAt('/help', null, 'nb')).w.text()).toContain('foreløpig bare på engelsk')
    expect((await mountAt('/help', null, 'en')).w.text()).not.toContain('in English for now')
  })
})
