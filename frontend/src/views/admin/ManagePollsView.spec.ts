import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import axios from 'axios'
import ManagePollsView from './ManagePollsView.vue'

vi.mock('axios')

const { routeQuery, replaceMock } = vi.hoisted(() => ({
  routeQuery: { value: {} as Record<string, string> },
  replaceMock: vi.fn()
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: routeQuery.value }),
  useRouter: () => ({ replace: replaceMock })
}))

interface Row { id: number; type: string; title: string; blocked: boolean; nationwide?: boolean; latestNote?: unknown }
function pollRow(r: Row) {
  return {
    ...r, status: 'PUBLISHED', creatorEmail: 'c@test.local', closeDate: null,
    zipcodes: ['94110'], stateInitial: 'CA', countyName: 'San Francisco',
    regionLabel: '', latestNote: null, ...r
  }
}

// Server state the mocked GET /api/admin/polls returns; tests mutate it to
// simulate a block/unblock landing.
let serverRows: Row[] = []
let lastListParams: Record<string, string> | undefined

beforeEach(() => {
  routeQuery.value = {}
  vi.useFakeTimers()
  serverRows = [
    { id: 1, type: 'QUESTIONNAIRE', title: 'Alpha', blocked: false },
    { id: 2, type: 'QUESTIONNAIRE', title: 'Bravo', blocked: true }
  ]
  vi.mocked(axios.get).mockImplementation(async (url: string, cfg?: any) => {
    if (url === '/api/admin/polls') {
      lastListParams = cfg?.params
      return { data: serverRows.map(pollRow) }
    }
    if (url === '/api/admin/polls/purview') return { data: { states: [], counties: [], zipcodes: [], unrestricted: false } }
    if (url === '/api/polls/search/suggestions') return { data: { titles: [], candidates: [] } }
    if (url.endsWith('/blocks')) {
      const id = Number(url.split('/')[5])
      return { data: serverRows.find(r => r.id === id)?.blocked ? [{ id: 900 + id, scope: 'ZIPCODE', zipcode: '94110' }] : [] }
    }
    return { data: [] }
  })
  vi.mocked(axios.post).mockImplementation(async (url: string) => {
    const id = Number(url.split('/')[5])
    serverRows = serverRows.map(r => (r.id === id ? { ...r, blocked: true } : r))
    return { data: {} }
  })
  vi.mocked(axios.delete).mockImplementation(async (url: string) => {
    const id = Number(url.split('/').pop()) - 900
    serverRows = serverRows.map(r => (r.id === id ? { ...r, blocked: false } : r))
    return { data: {} }
  })
})
afterEach(() => {
  vi.useRealTimers()
  vi.resetAllMocks()
})

async function mountView() {
  const wrapper = mount(ManagePollsView, { attachTo: document.body })
  await flushPromises()
  vi.advanceTimersByTime(200) // debounced initial fetch, if any
  await flushPromises()
  return wrapper
}
const titles = (w: ReturnType<typeof mount>) =>
  w.findAll('tbody tr').map(tr => tr.findAll('td')[0].text())
function enabledBox(w: ReturnType<typeof mount>, title: string) {
  const tr = w.findAll('tbody tr').find(t => t.findAll('td')[0].text() === title)!
  return tr.find('input[type="checkbox"]')
}

describe('ManagePollsView: disabled rows', () => {
  it('fetches disabled rows but hides them while "Show disabled" is off', async () => {
    const w = await mountView()
    expect(lastListParams?.includeDisabled).toBe('true')
    expect(titles(w)).toEqual(['Alpha'])
    w.unmount()
  })

  it('keeps a row visible (unchecked) after disabling it, and lets it be re-enabled in place', async () => {
    const w = await mountView()

    await enabledBox(w, 'Alpha').trigger('click')
    await flushPromises()
    const disableBtn = w.findAll('[role="dialog"] button').find(b => b.text() === 'Disable')
      ?? w.findAll('[role="dialog"] button').at(-1)!
    await disableBtn.trigger('click')
    await flushPromises()

    expect(titles(w)).toEqual(['Alpha'])
    expect((enabledBox(w, 'Alpha').element as HTMLInputElement).checked).toBe(false)

    await enabledBox(w, 'Alpha').trigger('click')
    await flushPromises()
    expect(titles(w)).toEqual(['Alpha'])
    expect((enabledBox(w, 'Alpha').element as HTMLInputElement).checked).toBe(true)
    w.unmount()
  })

  it('drops a disabled sticky row once a filter changes', async () => {
    const w = await mountView()
    await enabledBox(w, 'Alpha').trigger('click')
    await flushPromises()
    await w.findAll('[role="dialog"] button').at(-1)!.trigger('click')
    await flushPromises()
    expect(titles(w)).toEqual(['Alpha'])

    // Toggling "Show disabled" on and off ends stickiness: Alpha is now hidden.
    const showDisabled = w.findAll('label').find(l => l.text().includes('disabled'))!.find('input')
    await showDisabled.setValue(true)
    expect(titles(w).sort()).toEqual(['Alpha', 'Bravo'])
    await showDisabled.setValue(false)
    expect(titles(w)).toEqual([])
    w.unmount()
  })
})

describe('ManagePollsView: deep link from Manage Creators', () => {
  it('filters by creator, sorts by close date descending, and shows disabled polls', async () => {
    routeQuery.value = { creator: 'c@test.local', sort: 'closeDate', dir: 'desc', showDisabled: '1' }
    serverRows = [
      { id: 1, type: 'QUESTIONNAIRE', title: 'Alpha', blocked: false },
      { id: 2, type: 'QUESTIONNAIRE', title: 'Bravo', blocked: true }
    ]
    const w = await mountView()

    expect(lastListParams?.creatorEmail).toBe('c@test.local')
    expect(w.find('[data-test="creator-chip"]').text()).toContain('c@test.local')
    expect(titles(w).sort()).toEqual(['Alpha', 'Bravo']) // disabled Bravo shown
    expect(w.find('thead').text()).toContain('▼') // a descending sort indicator

    await w.find('[data-test="creator-chip"] button').trigger('click')
    expect(replaceMock).toHaveBeenCalledWith({ query: { sort: 'closeDate', dir: 'desc', showDisabled: '1' } })
    vi.advanceTimersByTime(200)
    await flushPromises()
    expect(lastListParams?.creatorEmail).toBeUndefined()
    w.unmount()
  })
})

describe('ManagePollsView: Show: columns', () => {
  afterEach(() => localStorage.clear())
  it('the zipcode column is headed Purview, there are no State or County columns, and unticking hides one', async () => {
    const w = await mountView()
    const headings = () => w.findAll('th').map(th => th.text().replace(/[▲▼]/g, '').trim())
    expect(headings()).toContain('Purview')
    expect(headings()).not.toContain('Zipcode')
    // State and County were dropped from the results (2026-10-28).
    expect(w.find('[data-test="column-chooser"]').text()).toBe('Columns to showShow:TitleTypeCreatorPurviewClosesEnabled?')
    expect(headings()).not.toContain('State')
    expect(headings()).not.toContain('County')
    await w.find('[data-test="show-purview"]').setValue(false)
    expect(headings()).not.toContain('Purview')
    expect(headings()).toContain('Notes')           // Notes and Actions always show
  })
})


describe('ManagePollsView: Show nationwide and notes (2026-10-28)', () => {
  it('hides nationwide polls until "Show nationwide" is ticked', async () => {
    serverRows = [
      { id: 1, type: 'QUESTIONNAIRE', title: 'Local', blocked: false },
      { id: 3, type: 'ELECTION', title: 'Everywhere', blocked: false, nationwide: true },
    ]
    const w = await mountView()
    expect(titles(w)).toEqual(['Local'])
    await w.find('[data-test="show-nationwide"]').setValue(true)
    expect(titles(w)).toEqual(['Everywhere', 'Local'])
  })

  it('the Notes cell previews 10 characters; its modal adds a note to the top of the list', async () => {
    const old = { id: 5, body: 'Checked the candidates list', emailed: false, createdAt: '2026-10-27T10:00:00Z', updatedAt: '2026-10-27T10:00:00Z' }
    serverRows = [{ id: 1, type: 'QUESTIONNAIRE', title: 'Alpha', blocked: false, latestNote: old }]
    const base = vi.mocked(axios.get).getMockImplementation()!
    vi.mocked(axios.get).mockImplementation(async (url: string, cfg?: any) =>
      url.endsWith('/notes') ? { data: [old] } : base(url, cfg))
    const fresh = { id: 6, body: 'Close date moved', emailed: false, createdAt: '2026-10-28T10:00:00Z', updatedAt: '2026-10-28T10:00:00Z' }
    vi.mocked(axios.post).mockResolvedValue({ data: fresh })

    const w = await mountView()
    expect(w.find('[data-test="notes-cell"]').text()).toBe('Checked th...')
    await w.find('[data-test="notes-cell"]').trigger('click')
    await flushPromises()
    expect(w.findAll('[data-test="notes-list"] li').map(li => li.text())).toEqual([expect.stringContaining('Checked the candidates list')])

    await w.find('[data-test="add-note"]').trigger('click')
    await flushPromises()
    await w.find('[data-test="note-body"]').setValue('Close date moved')
    await w.find('[data-test="note-save"]').trigger('click')
    await flushPromises()
    expect(axios.post).toHaveBeenCalledWith('/api/admin/polls/QUESTIONNAIRE/1/notes', { body: 'Close date moved', sendEmail: false })
    const list = w.findAll('[data-test="notes-list"] li').map(li => li.text())
    expect(list[0]).toContain('Close date moved')
    expect(list[1]).toContain('Checked the candidates list')
    expect(w.find('[data-test="notes-cell"]').text()).toBe('Close date...')
    expect(w.find('[data-test="add-note"]').exists()).toBe(true)   // the box closes after saving
  })
})

describe('ManagePollsView: cards on phones (2026-10-28)', () => {
  it('shows a card per poll with Notes, Edit and History, and Notes opens the dialog', async () => {
    const w = await mountView()
    const cards = w.findAll('[data-test="poll-cards"] li')
    expect(cards.map(c => c.text())).toEqual([expect.stringContaining('Alpha')])   // Bravo is disabled, hidden
    expect(cards[0].text()).toContain('Edit')
    expect(cards[0].text()).toContain('History')
    await cards[0].find('[data-test="card-notes"]').trigger('click')
    await flushPromises()
    expect(w.find('[data-test="notes-modal"]').exists()).toBe(true)
  })
})
