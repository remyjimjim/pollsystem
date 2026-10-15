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

function mountWith(widget: string) {
  vi.mocked(axios.get).mockImplementation(async (url: string) => {
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
  return mount(ElectionResponseForm, { props: { id: 7 }, attachTo: document.body })
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
