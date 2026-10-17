import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import axios from 'axios'
import ElectionForm from './ElectionForm.vue'
import { ScopeLevel } from '@/types'

vi.mock('axios')
const push = vi.fn()
vi.mock('vue-router', () => ({ useRouter: () => ({ push }) }))

const INITIAL = {
  id: 7, pollTypeId: 1, title: 'King for a week', date: '2026-12-01',
  scopeLevel: ScopeLevel.NATIONAL, regionIds: [], regionStateIds: [], closeDate: null,
  candidates: [{ name: 'A', affiliation: 'X', officeName: 'King' }, { name: 'B', affiliation: 'Y', officeName: 'King' }],
}
const OTHER = [
  { name: 'A', affiliation: 'X', officeName: 'King' },            // already on the form: not suggested
  { name: 'Jim James', affiliation: 'Red', officeName: 'District 1' },
]
const MEASURES = [
  { id: 31, title: 'Free Money Mondays', status: 'DRAFT' },
  { id: 32, title: 'Nap Time Act', status: 'PUBLISHED' },
]

async function mountForm() {
  vi.mocked(axios.get).mockImplementation(async (url: string) =>
    url === '/api/polls/ballot-measures' ? { data: MEASURES }
      : url === '/api/polls/elections/candidate-suggestions' ? { data: OTHER }
      : { data: [] })
  vi.mocked(axios.put).mockResolvedValue({ data: {} })
  vi.mocked(axios.post).mockResolvedValue({ data: {} })
  const w = mount(ElectionForm, {
    props: { pollTypeId: 1, initial: INITIAL },
    attachTo: document.body,
    global: { stubs: { 'router-link': RouterLinkStub, PurviewSetter: true } },
  })
  await flushPromises()
  return w
}
const button = (w: any, text: string) => w.findAll('button').find((b: any) => b.text() === text)

afterEach(() => {
  vi.resetAllMocks()
  document.body.innerHTML = ''
})

describe('ElectionForm: its ballot measures', () => {
  it('lists them with their status: Edit for drafts, View results once published', async () => {
    const w = await mountForm()
    const rows = w.findAll('[data-test="election-measure"]')
    expect(rows.map(r => r.text())).toEqual([
      expect.stringContaining('Free Money Mondays'), expect.stringContaining('Nap Time Act'),
    ])
    expect(rows[0].find('[data-test="edit-measure"]').exists()).toBe(true)
    expect(rows[1].find('[data-test="edit-measure"]').exists()).toBe(false)
    expect(rows[1].findComponent(RouterLinkStub).props('to')).toBe('/polls/ballot-measure/32/results')
    w.unmount()
  })

  it('Edit goes straight to the measure when nothing changed', async () => {
    const w = await mountForm()
    await w.find('[data-test="edit-measure"]').trigger('click')
    expect(w.find('[data-test="save-before-leaving"]').exists()).toBe(false)
    expect(push).toHaveBeenCalledWith('/creator/polls/ballot-measure/31/edit')
    w.unmount()
  })

  it('with unsaved changes it asks first: Save and go saves the draft, then goes', async () => {
    const w = await mountForm()
    await w.find('input').setValue('King for a month')
    await w.find('[data-test="edit-measure"]').trigger('click')
    expect(w.find('[data-test="save-before-leaving"]').exists()).toBe(true)
    expect(push).not.toHaveBeenCalled()

    await w.find('[data-test="save-and-go"]').trigger('click')
    await flushPromises()
    expect(axios.put).toHaveBeenCalledWith('/api/polls/elections/7', expect.objectContaining({ title: 'King for a month' }))
    expect(push).toHaveBeenCalledWith('/creator/polls/ballot-measure/31/edit')
    w.unmount()
  })

  it('Go without saving leaves the changes unsaved', async () => {
    const w = await mountForm()
    await w.find('input').setValue('King for a month')
    await w.find('[data-test="edit-measure"]').trigger('click')
    await w.find('[data-test="leave-without-saving"]').trigger('click')
    await flushPromises()
    expect(axios.put).not.toHaveBeenCalled()
    expect(push).toHaveBeenCalledWith('/creator/polls/ballot-measure/31/edit')
    w.unmount()
  })

  it('Publish lists the draft measures that go live with it', async () => {
    const w = await mountForm()
    await button(w, 'Publish').trigger('click')
    const ask = w.find('[data-test="publish-with-measures"]')
    expect(ask.text()).toContain('Free Money Mondays')
    expect(ask.text()).not.toContain('Nap Time Act')
    expect(axios.post).not.toHaveBeenCalled()

    await w.find('[data-test="publish-all"]').trigger('click')
    await flushPromises()
    expect(axios.post).toHaveBeenCalledWith('/api/polls/elections/7/publish', null, { params: { confirmed: false } })
    w.unmount()
  })
})

describe('ElectionForm: candidate Suggestions', () => {
  const names = (w: any) => w.findAll('input[placeholder="Name"]').map((i: any) => (i.element as HTMLInputElement).value)

  it('a removed candidate can be restored from Suggestions', async () => {
    const w = await mountForm()
    await w.findAll('button[aria-label="Remove candidate"]')[1].trigger('click')   // remove B
    expect(names(w)).toEqual(['A'])

    await w.find('[data-test="toggle-suggestions"]').trigger('click')
    await flushPromises()
    const removed = w.findAll('[data-test="removed-suggestion"]')
    expect(removed.map(r => r.text())).toEqual([expect.stringContaining('B')])
    await removed[0].find('button').trigger('click')
    expect(names(w)).toEqual(['A', 'B'])
    expect(w.findAll('[data-test="removed-suggestion"]')).toHaveLength(0)
    w.unmount()
  })

  it('offers candidates from your other elections, skipping ones already on the form', async () => {
    const w = await mountForm()
    await w.find('[data-test="toggle-suggestions"]').trigger('click')
    await flushPromises()
    expect(axios.get).toHaveBeenCalledWith('/api/polls/elections/candidate-suggestions', { params: { excludeElectionId: 7 } })
    const other = w.findAll('[data-test="other-suggestion"]')
    expect(other.map(o => o.text())).toEqual([expect.stringContaining('Jim James')])

    await other[0].find('button').trigger('click')
    expect(names(w)).toEqual(['A', 'B', 'Jim James'])
    expect(w.findAll('[data-test="other-suggestion"]')).toHaveLength(0)
    w.unmount()
  })
})

