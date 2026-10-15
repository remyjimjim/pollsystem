import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import axios from 'axios'
import PollSearchView from './PollSearchView.vue'

vi.mock('axios')

const row = (id: number, creatorEmail: string | null) => ({
  id, type: 'Questionnaire', title: `Poll ${id}`, closeDate: null,
  zipcodes: [{ code: '90001', state: 'CA' }], regionLabel: null, creatorEmail,
})

async function searchReturning(results: unknown[]) {
  vi.mocked(axios.get).mockImplementation(async (url: string) => {
    if (url === '/api/polls/search') return { data: results }
    if (url === '/api/polls/search/suggestions') return { data: { titles: [], candidateNames: [] } }
    return { data: [] }
  })
  const w = mount(PollSearchView, {
    attachTo: document.body,
    global: { plugins: [createTestingPinia({ createSpy: vi.fn })], stubs: { 'router-link': RouterLinkStub } },
  })
  await flushPromises()
  await w.find('form').trigger('submit')
  await flushPromises()
  return w
}

afterEach(() => {
  vi.resetAllMocks()
  document.body.innerHTML = ''
})

describe('PollSearchView: the Creator column', () => {
  it('admins and supers (emails sent) get a short link to Manage Creators', async () => {
    const w = await searchReturning([row(1, 'alexandra@test.local'), row(2, 'bo@x.io')])
    const headers = w.findAll('th').map(h => h.text())
    expect(headers.findIndex(h => h.startsWith('Creator'))).toBe(headers.findIndex(h => h.startsWith('Zipcode')) + 1)

    const links = w.findAll('[data-test="creator"]').map(c => c.findComponent(RouterLinkStub))
    expect(links.map(l => l.text())).toEqual(['alexa...', 'bo@x....'])
    expect(links[0].props('to')).toEqual({ path: '/admin/manage-creators', query: { creator: 'alexandra@test.local' } })
    expect(links[0].attributes('title')).toBe('alexandra@test.local')
    w.unmount()
  })

  it('everyone else (no emails sent) gets no Creator column', async () => {
    const w = await searchReturning([row(1, null)])
    expect(w.findAll('th').map(h => h.text()).some(h => h.startsWith('Creator'))).toBe(false)
    expect(w.find('[data-test="creator"]').exists()).toBe(false)
    w.unmount()
  })

  it('a ballot measure shows its election (15 characters + "...", full title on hover), and Vote goes there', async () => {
    const auth = (await import('@/stores/auth')).useAuthStore
    const measure = { ...row(5, null), type: 'BallotMeasure', electionId: 9, electionTitle: 'King for a week of fun' }
    const w = await searchReturning([measure, row(1, null)])
    expect(w.findAll('th').map(h => h.text()).some(h => h.startsWith('For Election'))).toBe(true)
    const cells = w.findAll('[data-test="for-election"]')
    expect(cells[0].text()).toBe('King for a week...')
    expect(cells[0].find('span').attributes('title')).toBe('King for a week of fun')
    expect(cells[1].text()).toBe('')

    // Signed in: Vote on the measure opens its election, scrolled to it.
    ;(auth() as any).isAuthenticated = true
    await flushPromises()
    const vote = w.findAllComponents(RouterLinkStub).find(l => typeof l.props('to') === 'object' && (l.props('to') as any).path?.startsWith('/polls/election/'))
    expect(vote?.props('to')).toEqual({ path: '/polls/election/9', query: { measure: '5' } })
    w.unmount()
  })
})

