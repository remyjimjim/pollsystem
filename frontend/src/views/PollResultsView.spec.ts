import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import axios from 'axios'
import PollResultsView from './PollResultsView.vue'

vi.mock('axios')
vi.mock('vue-router', () => ({ useRoute: () => ({ params: { type: 'ballot-measure', id: '97' } }) }))

async function areaLine(purviewLevel: string, purviewAreas: string[]) {
  vi.mocked(axios.get).mockImplementation(async (url: string) => {
    if (url.includes('/results')) return { data: {
      measureId: 97, title: 'Bond measure', totalRespondents: 3, yes: 2, no: 1,
      filterApplied: null, suppressed: false, suppressionMessage: null, purviewLevel, purviewAreas,
    } }
    return { data: [] }
  })
  const w = mount(PollResultsView)
  await flushPromises()
  return w.find('[data-test="purview-area"]').text()
}

afterEach(() => vi.resetAllMocks())

describe('PollResultsView: where the poll is for', () => {
  it('says Nationwide, or names the places', async () => {
    expect(await areaLine('NATIONAL', [])).toBe('Purview area: Nationwide')
    expect(await areaLine('STATE', ['Colorado', 'Utah'])).toBe('Purview area: Colorado, Utah')
  })

  it('shows the first five places and how many more', async () => {
    const counties = ['A (CO)', 'B (CO)', 'C (CO)', 'D (CO)', 'E (CO)', 'F (CO)', 'G (CO)']
    expect(await areaLine('COUNTY', counties)).toBe('Purview area: A (CO), B (CO), C (CO), D (CO), E (CO) +2 more')
  })
})
