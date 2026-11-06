import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import axios from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import ManagePollsView from './ManagePollsView.vue'
import { useAuthStore } from '@/stores/auth'

vi.mock('axios')

const { routeQuery, replaceMock } = vi.hoisted(() => ({
  routeQuery: { value: {} as Record<string, string> },
  replaceMock: vi.fn()
}))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: routeQuery.value }),
  useRouter: () => ({ replace: replaceMock })
}))

interface Row { id: number; type: string; title: string; blocked: boolean; nationwide?: boolean; latestNote?: unknown; status?: string; closeDate?: string | null }
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
  setActivePinia(createPinia())
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

    // Ticking Status → Disabled on and off ends stickiness: Alpha is now hidden.
    const showDisabled = w.find('[data-test="status-filter-disabled"]')
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
  it('hides nationwide polls until Status → Nationwide is ticked', async () => {
    serverRows = [
      { id: 1, type: 'QUESTIONNAIRE', title: 'Local', blocked: false },
      { id: 3, type: 'ELECTION', title: 'Everywhere', blocked: false, nationwide: true },
    ]
    const w = await mountView()
    expect(titles(w)).toEqual(['Local'])
    await w.find('[data-test="status-filter-nationwide"]').setValue(true)
    expect(titles(w)).toEqual(['Everywhere', 'Local'])
  })

  it('Status shows polls by stage: a passed close date counts as Closed; unticking a stage hides it', async () => {
    serverRows = [
      { id: 1, type: 'QUESTIONNAIRE', title: 'Open', blocked: false, status: 'PUBLISHED', closeDate: null },
      { id: 2, type: 'ELECTION', title: 'Past', blocked: false, status: 'PUBLISHED', closeDate: '2026-11-03T20:00:00Z' },
      { id: 3, type: 'QUESTIONNAIRE', title: 'Sketch', blocked: false, status: 'DRAFT', closeDate: null },
    ]
    const w = await mountView()
    expect(titles(w).sort()).toEqual(['Open', 'Past', 'Sketch'])
    await w.find('[data-test="status-filter-closed"]').setValue(false)
    expect(titles(w).sort()).toEqual(['Open', 'Sketch'])
    await w.find('[data-test="status-filter-draft"]').setValue(false)
    expect(titles(w)).toEqual(['Open'])
    await w.find('[data-test="status-filter-published"]').setValue(false)
    await w.find('[data-test="status-filter-closed"]').setValue(true)
    expect(titles(w)).toEqual(['Past'])
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

describe('ManagePollsView: Remove close dates (2026-11-06)', () => {
  const rows = [
    { id: 1, type: 'ELECTION', title: 'Past', blocked: false, status: 'PUBLISHED', closeDate: '2026-11-03T20:00:00Z' },
    { id: 2, type: 'QUESTIONNAIRE', title: 'Future', blocked: false, status: 'PUBLISHED', closeDate: '2099-01-01T00:00:00Z' },
    { id: 3, type: 'QUESTIONNAIRE', title: 'Open', blocked: false, status: 'PUBLISHED', closeDate: null },
  ]

  it('is hidden from an admin', async () => {
    useAuthStore().user = { access: 'ADMIN' } as any
    serverRows = rows
    const w = await mountView()
    expect(w.find('[data-test="remove-close-dates"]').exists()).toBe(false)
    expect(w.find('[data-test="set-close-date"]').exists()).toBe(false)
    w.unmount()
  })

  it('a super clears the listed close dates after typing the count, and only the listed polls are sent', async () => {
    useAuthStore().user = { access: 'SUPER' } as any
    serverRows = rows
    const w = await mountView()
    const button = w.find('[data-test="remove-close-dates"]')
    expect(button.text()).toContain('(2)')
    await button.trigger('click')
    const modal = w.find('[data-test="remove-close-dates-modal"]')
    expect(modal.text()).toContain('1 of them have closed')
    const confirm = w.find('[data-test="remove-close-dates-confirm"]')
    expect((confirm.element as HTMLButtonElement).disabled).toBe(true)
    await w.find('[data-test="remove-close-dates-typed"]').setValue('2')
    vi.mocked(axios.post).mockResolvedValue({ data: { cleared: 2, reopened: 1 } })
    await confirm.trigger('click')
    await flushPromises()
    expect(vi.mocked(axios.post)).toHaveBeenCalledWith('/api/admin/polls/remove-close-dates',
      { polls: [{ type: 'ELECTION', id: 1 }, { type: 'QUESTIONNAIRE', id: 2 }] })
    expect(w.text()).toContain('Removed 2 close dates; 1 polls reopened for voting.')
    w.unmount()
  })
})

describe('ManagePollsView: out-of-order searches (2026-11-06)', () => {
  it('keeps the newest search\'s rows when an older one answers last', async () => {
    const w = await mountView()
    const pending: Array<(v: unknown) => void> = []
    vi.mocked(axios.get).mockImplementation((url: string) => url === '/api/admin/polls'
      ? new Promise(res => pending.push(res)) as any
      : Promise.resolve({ data: [] }) as any)
    const box = w.find('[data-test="type-filter-election"]')
    await box.setValue(false); vi.advanceTimersByTime(500); await flushPromises()
    await box.setValue(true); vi.advanceTimersByTime(500); await flushPromises()
    expect(pending.length).toBe(2)
    pending[1]({ data: [pollRow({ id: 7, type: 'QUESTIONNAIRE', title: 'Newer', blocked: false })] })
    await flushPromises()
    pending[0]({ data: [pollRow({ id: 8, type: 'QUESTIONNAIRE', title: 'Older', blocked: false })] })
    await flushPromises()
    expect(titles(w)).toEqual(['Newer'])
    w.unmount()
  })
})

describe('ManagePollsView: Edit saves only what changed (2026-11-06)', () => {
  const row = { id: 4, type: 'ELECTION', title: 'District 2', blocked: false, status: 'PUBLISHED', closeDate: '2026-11-04T01:00:00Z' }
  async function openEdit() {
    serverRows = [row]
    const w = await mountView()
    await w.find('[data-test="card-edit"]').trigger('click')
    return w
  }
  const save = (w: ReturnType<typeof mount>) => w.findAll('[role="dialog"] button').find(b => b.text() === 'Save')!.trigger('click')

  it('saves nothing when nothing changed, and says so', async () => {
    const w = await openEdit()
    await save(w); await flushPromises()
    expect(vi.mocked(axios.put)).not.toHaveBeenCalled()
    expect(w.text()).toContain('No changes, so nothing was saved.')
    expect(w.find('[role="dialog"]').exists()).toBe(false)
    w.unmount()
  })

  it('a reason alone is not a change: the modal stays open and points to Notes', async () => {
    const w = await openEdit()
    await w.find('#edit-reason').setValue('abc')
    await save(w); await flushPromises()
    expect(vi.mocked(axios.put)).not.toHaveBeenCalled()
    expect(w.find('[role="dialog"]').text()).toContain('use Notes')
    w.unmount()
  })

  it('sends only the changed field', async () => {
    const w = await openEdit()
    vi.mocked(axios.post).mockResolvedValue({ data: { found: [[], [], []] } })
    vi.mocked(axios.put).mockResolvedValue({ data: { id: 1, body: '[status → ARCHIVED]' } })
    await w.find('[role="dialog"] select').setValue('ARCHIVED')
    await save(w); await flushPromises()
    expect(vi.mocked(axios.put)).toHaveBeenCalledWith('/api/admin/polls/ELECTION/4',
      { title: null, summary: null, status: 'ARCHIVED', closeDate: null, reason: null, notifyCreator: false })
    w.unmount()
  })
})

describe('ManagePollsView: Set close date (2026-11-06)', () => {
  const rows = [
    { id: 1, type: 'ELECTION', title: 'Past', blocked: false, status: 'PUBLISHED', closeDate: '2026-11-03T20:00:00Z' },
    { id: 2, type: 'QUESTIONNAIRE', title: 'Open', blocked: false, status: 'PUBLISHED', closeDate: null },
  ]

  it('a super sets one date on every listed poll after picking it and typing the count', async () => {
    useAuthStore().user = { access: 'SUPER' } as any
    serverRows = rows
    const w = await mountView()
    const button = w.find('[data-test="set-close-date"]')
    expect(button.text()).toContain('(2)')
    await button.trigger('click')
    const confirm = w.find('[data-test="set-close-date-confirm"]')
    await w.find('[data-test="set-close-date-typed"]').setValue('2')
    expect((confirm.element as HTMLButtonElement).disabled).toBe(true) // no date yet
    await w.find('[data-test="set-close-date-input"]').setValue('2099-12-01T05:00')
    expect(w.find('[data-test="set-close-date-modal"]').text()).toContain('1 of them have closed and will reopen')
    vi.mocked(axios.post).mockResolvedValue({ data: { updated: 2, reopened: 1, closed: 0 } })
    await confirm.trigger('click')
    await flushPromises()
    expect(vi.mocked(axios.post)).toHaveBeenCalledWith('/api/admin/polls/set-close-date', {
      polls: [{ type: 'ELECTION', id: 1 }, { type: 'QUESTIONNAIRE', id: 2 }],
      closeDate: new Date('2099-12-01T05:00').toISOString(),
    })
    expect(w.text()).toContain('Set 2 close dates; 1 polls reopened, 0 closed.')
    w.unmount()
  })

  it('warns when the date has already passed', async () => {
    useAuthStore().user = { access: 'SUPER' } as any
    serverRows = rows
    const w = await mountView()
    await w.find('[data-test="set-close-date"]').trigger('click')
    await w.find('[data-test="set-close-date-input"]').setValue('2020-01-01T00:00')
    expect(w.find('[data-test="set-close-date-modal"]').text()).toContain('This date has passed: 1 open polls will close now.')
    w.unmount()
  })
})

describe('ManagePollsView: Poll type label and All (2026-11-06)', () => {
  it('shows "Poll type" above the button, and All ticks or clears every type', async () => {
    const w = await mountView()
    const box = w.find('[data-test="type-filter"]')
    expect(box.find('span').text()).toBe('Poll type')
    const button = w.find('[data-test="type-filter-button"]')
    expect(button.text()).toBe('All▾')
    // Status the same way (label above, the button shows only the summary).
    expect(w.find('[data-test="status-filter"] span').text()).toBe('Status')
    expect(w.find('[data-test="status-filter-button"]').text()).toBe('3 selected▾')
    const all = w.find('[data-test="type-filter-all"]')
    expect((all.element as HTMLInputElement).checked).toBe(true)
    await all.setValue(false)
    expect(button.text()).toBe('None▾')
    expect((w.find('[data-test="type-filter-election"]').element as HTMLInputElement).checked).toBe(false)
    await w.find('[data-test="type-filter-election"]').setValue(true)
    expect((all.element as HTMLInputElement).indeterminate).toBe(true)
    await all.setValue(true)
    expect(button.text()).toBe('All▾')
    w.unmount()
  })
})

describe('ManagePollsView: Status All (2026-11-06)', () => {
  it('All ticks every Status option, including Disabled and Nationwide, and shows those polls', async () => {
    serverRows = [
      { id: 1, type: 'QUESTIONNAIRE', title: 'Plain', blocked: false },
      { id: 2, type: 'QUESTIONNAIRE', title: 'Switched off', blocked: true },
      { id: 3, type: 'ELECTION', title: 'Everywhere', blocked: false, nationwide: true },
    ]
    const w = await mountView()
    const all = w.find('[data-test="status-filter-all"]')
    expect((all.element as HTMLInputElement).indeterminate).toBe(true) // 3 of 5 by default
    expect(titles(w)).toEqual(['Plain'])
    await all.setValue(true)
    expect(w.find('[data-test="status-filter-button"]').text()).toBe('All▾')
    expect(titles(w).sort()).toEqual(['Everywhere', 'Plain', 'Switched off'])
    await all.setValue(false)
    expect(titles(w)).toEqual([])
    w.unmount()
  })
})

