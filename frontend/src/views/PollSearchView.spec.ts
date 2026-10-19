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
  localStorage.clear()
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

describe('PollSearchView: Responses column and the column chooser', () => {
  const headers = (w: any) => w.findAll('th').map((h: any) => h.text().replace(/[▲▼]/g, '').trim()).filter(Boolean)

  it('shows how many responded, and sorts by it as a number', async () => {
    const w = await searchReturning([{ ...row(1, null), respondents: 9 }, { ...row(2, null), respondents: 10 }, { ...row(3, null), respondents: 0 }])
    expect(w.findAll('[data-test="responses"]').map(c => c.text())).toEqual(['9', '10', '0'])
    await w.findAll('th').find(h => h.text().startsWith('Responses'))!.trigger('click')
    expect(w.findAll('[data-test="responses"]').map(c => c.text())).toEqual(['0', '9', '10'])
    w.unmount()
  })

  it('unticking a column hides it, and it stays hidden next time', async () => {
    const w = await searchReturning([row(1, null)])
    expect(headers(w)).toContain('Zipcodes')
    const box = w.find('[data-test="column-chooser"]').findAll('label').find(l => l.text() === 'Zipcodes')!.find('input')
    await box.setValue(false)
    expect(headers(w)).not.toContain('Zipcodes')
    expect(headers(w)).toContain('Title')                     // always shown
    expect(JSON.parse(localStorage.getItem('pollSearch.columns')!).zipcodes).toBe(false)
    w.unmount()

    const again = await searchReturning([row(1, null)])
    expect(headers(again)).not.toContain('Zipcodes')
    again.unmount()
  })

  it('on a narrow screen, the wide columns start hidden', async () => {
    const width = window.innerWidth
    Object.defineProperty(window, 'innerWidth', { value: 390, configurable: true })
    try {
      const w = await searchReturning([row(1, 'a@b.c')])
      expect(headers(w)).not.toContain('Zipcodes')
      expect(headers(w)).not.toContain('Creator')
      expect(headers(w)).toEqual(expect.arrayContaining(['Title', 'Type', 'Responses', 'Closes']))
      w.unmount()
    } finally {
      Object.defineProperty(window, 'innerWidth', { value: width, configurable: true })
    }
  })
})

