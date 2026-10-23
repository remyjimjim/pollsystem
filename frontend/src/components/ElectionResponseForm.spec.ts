import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import axios from 'axios'
import ElectionResponseForm from './ElectionResponseForm.vue'

vi.mock('axios')
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))

const CANDIDATES = [
  { id: 11, name: 'Avery Stone', affiliation: 'Independent', officeName: 'Mayor' },
  { id: 12, name: 'Jordan Reyes', affiliation: 'Civic Party', officeName: 'Mayor' },
]

const MEASURES = [{ id: 31, title: 'Free Money Mondays', summary: 'Free money?', effectiveDate: '2026-12-01', closeDate: null }]

function mountWith(widget: string, opts: { measures?: typeof MEASURES; focusMeasure?: number } = {}) {
  vi.mocked(axios.get).mockImplementation(async (url: string) => {
    if (url === '/api/polls/ballot-measures') return { data: opts.measures ?? [] }
    if (url.startsWith('/api/polls/ballot-measures/') && url.endsWith('/responses/me')) {
      return { data: { measureId: 31, hasResponse: false, response: null, comment: null, dateSubmitted: null } }
    }
    if (url.endsWith('/responses/me')) {
      return { data: { electionId: 7, hasResponses: false, firstSubmittedAt: null, responses: [] } }
    }
    return {
      data: {
        id: 7, title: 'City election', date: '2026-11-03', zipcode: null, status: 'PUBLISHED',
        closeDate: null, candidates: CANDIDATES, candidatesWidget: widget, candidatesGroupBy: 'officeName',
      },
    }
  })
  return mount(ElectionResponseForm, { props: { id: 7, focusMeasure: opts.focusMeasure ?? null }, attachTo: document.body })
}

afterEach(() => {
  vi.resetAllMocks()
  document.body.innerHTML = ''
})

describe('ElectionResponseForm: candidate choices are labelled', () => {
  for (const [widget, type] of [
    ['selectOneRadio', 'radio'],
    ['selectOneCheckbox', 'checkbox'],
    ['selectManyCheckbox', 'checkbox'],
  ] as const) {
    it(`${widget}: each ${type} is named by its candidate, and clicking the name selects it`, async () => {
      const w = mountWith(widget)
      await flushPromises()

      for (const c of CANDIDATES) {
        const input = w.find<HTMLInputElement>(`input#candidate-${c.id}`)
        expect(input.exists()).toBe(true)
        expect(input.attributes('type')).toBe(type)
        const label = w.find(`label[for="candidate-${c.id}"]`)
        expect(label.text()).toContain(c.name)
        expect(label.text()).toContain(c.affiliation)
        // The comment box is NOT inside the label (typing there mustn't toggle).
        expect(label.find('input').exists()).toBe(false)
      }

      // Clicking a candidate's name selects their choice.
      const avery = w.find<HTMLInputElement>('input#candidate-11')
      expect(avery.element.checked).toBe(false)
      ;(w.find('label[for="candidate-11"]').element as HTMLLabelElement).click()
      await flushPromises()
      expect(avery.element.checked).toBe(true)
      w.unmount()
    })
  }
})

describe('ElectionResponseForm: public comments', () => {
  it('asks to replace a listed word: Yes replaces it, No keeps that comment private', async () => {
    const darn = { word: 'darn', replace: 'dang' }
    vi.mocked(axios.post).mockImplementation(async (url: string) =>
      url === '/api/moderation/check' ? { data: { found: [[darn], [darn]] } } : { data: {} })
    const w = mountWith('selectOneRadio')
    await flushPromises()
    await w.find('input#candidate-11').setValue(true)
    await w.find('input#comment-c-11').setValue('Darn good mayor')
    await w.find('input#comment-public-c-11').setValue(true)
    await w.find('input#comment-c-12').setValue('darn')
    await w.find('input#comment-public-c-12').setValue(true)

    await w.find('button').trigger('click')
    await flushPromises()
    const dialog = () => w.find('[data-test="replace-word-dialog"]')
    expect(dialog().exists()).toBe(true)
    expect(document.activeElement?.id).toBe('comment-c-11')     // the comment being asked about
    await w.find('[data-test="replace-yes"]').trigger('click')
    await flushPromises()
    expect(document.activeElement?.id).toBe('comment-c-12')
    await w.find('[data-test="replace-no"]').trigger('click')
    await flushPromises()

    expect(dialog().exists()).toBe(false)
    const [check, save] = vi.mocked(axios.post).mock.calls
    expect(check).toEqual(['/api/moderation/check', { texts: ['Darn good mayor', 'darn'] }])
    const sent = save[1] as { answers: any[] }
    expect(sent.answers).toEqual([
      { candidateId: 11, response: true, comment: 'Dang good mayor', commentPublic: true },
      { candidateId: 12, response: false, comment: 'darn', commentPublic: false },
    ])
    expect((w.find('input#comment-public-c-12').element as HTMLInputElement).checked).toBe(false)
    w.unmount()
  })
})

describe('ElectionResponseForm: ballot measures on the ballot', () => {
  it('lists the measures; an answered one is submitted with the candidates, a blank one is skipped', async () => {
    vi.mocked(axios.post).mockResolvedValue({ data: {} })
    const two = [...MEASURES, { id: 32, title: 'Nap Time Act', summary: 'Naps?', effectiveDate: '2026-12-01', closeDate: null }]
    const w = mountWith('selectOneRadio', { measures: two })
    await flushPromises()
    expect(w.findAll('[data-test="ballot-measure"]').map(f => f.find('legend').text())).toEqual(['Free Money Mondays', 'Nap Time Act'])

    await w.find('input#candidate-11').setValue(true)
    await w.find('input#measure-31-yes').setValue(true)
    await w.find('input#comment-m-31').setValue('Yes please')
    await w.find('button').trigger('click')
    await flushPromises()

    const posts = vi.mocked(axios.post).mock.calls.map(c => c[0])
    expect(posts).toEqual(['/api/polls/elections/7/responses', '/api/polls/ballot-measures/31/responses'])
    expect(vi.mocked(axios.post).mock.calls[1][1]).toEqual({ response: true, comment: 'Yes please', commentPublic: false })
    w.unmount()
  })

  it('arriving from a measure\'s Vote link highlights that measure', async () => {
    const w = mountWith('selectOneRadio', { measures: MEASURES, focusMeasure: 31 })
    await flushPromises()
    expect(w.find('#measure-31').classes()).toContain('border-sky-400')
    w.unmount()
  })
})


describe('ElectionResponseForm: an office still to choose', () => {
  const TWO_OFFICES = [
    ...CANDIDATES,
    { id: 13, name: 'Sam Lee', affiliation: 'Nonpartisan', officeName: 'Supreme Court Justice Position 1' },
    { id: 14, name: 'Ari Moss', affiliation: 'Nonpartisan', officeName: 'Supreme Court Justice Position 1' },
  ]
  function mountTwoOffices() {
    CANDIDATES.push(...TWO_OFFICES.slice(2))
    return mountWith('selectOneRadio', { measures: MEASURES })
  }
  afterEach(() => { CANDIDATES.splice(2) })

  it('keeps the form on screen, links the office, and the link goes to it', async () => {
    const w = mountTwoOffices()
    await flushPromises()
    await w.find('input#candidate-11').setValue(true)        // Mayor chosen
    await w.find('input#measure-31-yes').setValue(true)      // a measure answered too
    await w.find('button').trigger('click')
    await flushPromises()

    expect(axios.post).not.toHaveBeenCalled()
    const msg = w.find('[data-test="missing-choice"]')
    expect(msg.text()).toBe('Please make a selection for: Supreme Court Justice Position 1')
    // The form, and what was already chosen, stay on screen.
    expect((w.find('input#candidate-11').element as HTMLInputElement).checked).toBe(true)
    expect((w.find('input#measure-31-yes').element as HTMLInputElement).checked).toBe(true)

    const link = msg.find('a')
    const section = w.find(link.attributes('href')!)
    expect(section.find('legend').text()).toBe('Supreme Court Justice Position 1')
    expect(section.attributes('data-missing')).toBe('true')
    await link.trigger('click')
    expect(document.activeElement).toBe(w.find('input#candidate-13').element)

    // Choosing someone there clears the message.
    await w.find('input#candidate-13').setValue(true)
    expect(w.find('[data-test="missing-choice"]').exists()).toBe(false)
    expect(section.attributes('data-missing')).toBeUndefined()
    w.unmount()
  })

  it('a failed submission is shown by the button, without hiding the form', async () => {
    vi.mocked(axios.post).mockRejectedValue({ response: { data: { message: 'Voting on this election has closed' } } })
    const w = mountWith('selectOneRadio')
    await flushPromises()
    await w.find('input#candidate-11').setValue(true)
    await w.find('button').trigger('click')
    await flushPromises()
    expect(w.find('[data-test="submit-error"]').text()).toBe('Voting on this election has closed')
    expect(w.find('input#candidate-11').exists()).toBe(true)
    w.unmount()
  })
})
