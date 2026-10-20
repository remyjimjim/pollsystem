import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import axios from 'axios'
import PollResultsView from './PollResultsView.vue'

vi.mock('axios')
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { type: 'ballot-measure', id: '97' } }) }))

const visibility = (mode: string, soFar = 12) => ({ mode, respondentsSoFar: soFar, minimum: 10, step: 10 })
const measure = (over: Record<string, unknown> = {}) => ({
  measureId: 97, title: 'Bond measure', totalRespondents: null, yes: null, no: null, yesPct: 60, noPct: 40,
  filterApplied: null, suppressed: false, suppressionMessage: null,
  purviewLevel: 'NATIONAL', purviewAreas: [], visibility: visibility('STEPPED'), ...over,
})

async function mountWith(data: unknown) {
  vi.mocked(axios.get).mockImplementation(async (url: string) => ({ data: url.includes('/results') ? data : [] }))
  const w = mount(PollResultsView)
  await flushPromises()
  return w
}

afterEach(() => vi.resetAllMocks())

describe('PollResultsView: where the poll is for', () => {
  it('says Nationwide, or names the places', async () => {
    expect((await mountWith(measure())).find('[data-test="purview-area"]').text()).toBe('Purview area: Nationwide')
    expect((await mountWith(measure({ purviewLevel: 'STATE', purviewAreas: ['Colorado', 'Utah'] })))
      .find('[data-test="purview-area"]').text()).toBe('Purview area: Colorado, Utah')
  })

  it('shows the first five places and how many more', async () => {
    const counties = ['A (CO)', 'B (CO)', 'C (CO)', 'D (CO)', 'E (CO)', 'F (CO)', 'G (CO)']
    expect((await mountWith(measure({ purviewLevel: 'COUNTY', purviewAreas: counties })))
      .find('[data-test="purview-area"]').text()).toBe('Purview area: A (CO), B (CO), C (CO), D (CO), E (CO) +2 more')
  })
})

describe('PollResultsView: small-poll privacy', () => {
  it('hides results below the minimum, saying how many have responded and why', async () => {
    const w = await mountWith(measure({ visibility: visibility('HIDDEN', 4), yesPct: 0, noPct: 0 }))
    expect(w.find('[data-test="privacy-caption"]').text())
      .toBe("For privacy, results aren't shown until at least 10 people have voted. Results are updated every 10 voters.")
    expect(w.find('[data-test="hidden"]').text()).toBe('4 people have responded so far.')
    expect(w.text()).not.toContain('%')
    expect(w.find('form').exists()).toBe(false) // nothing to filter yet
  })

  it('shows rounded percentages only, with the caption', async () => {
    const w = await mountWith(measure())
    expect(w.find('[data-test="privacy-caption"]').exists()).toBe(true)
    expect(w.text()).toContain('60%')
    expect(w.text()).toContain('40%')
    expect(w.text()).not.toContain('respondent(s)')
  })

  it('a super sees live raw numbers, without the caption', async () => {
    const w = await mountWith(measure({ visibility: visibility('LIVE', 5), totalRespondents: 5, yes: 3, no: 2 }))
    expect(w.find('[data-test="privacy-caption"]').exists()).toBe(false)
    expect(w.find('[data-test="live-note"]').exists()).toBe(true)
    expect(w.text()).toContain('3 · 60%')
    expect(w.text()).toContain('5 respondent(s)')
  })

  it('each purview checkbox shows how many responded inside / outside, or that it is withheld', async () => {
    const shown = await mountWith(measure({ purviewCounts: { inside: 32, outside: 14 } }))
    expect(shown.find('[data-test="within-count"]').text()).toBe('(32)')
    expect(shown.find('[data-test="outside-count"]').text()).toBe('(14)')

    const withheld = await mountWith(measure({ purviewCounts: { inside: null, outside: null } }))
    expect(withheld.find('[data-test="within-count"]').text()).toBe('(fewer than 10)')
    expect(withheld.find('[data-test="outside-count"]').text()).toBe('(fewer than 10)')
  })
})

