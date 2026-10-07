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
