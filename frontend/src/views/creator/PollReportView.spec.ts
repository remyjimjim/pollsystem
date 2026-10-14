import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import axios from 'axios'
import PollReportView from './PollReportView.vue'

vi.mock('axios')
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { type: 'election', id: '7' } }),
  RouterLink: { template: '<a><slot/></a>' },
}))

const REPORT = {
  respondents: 40, inArea: 30, threshold: 10,
  timeline: [
    { from: '2026-10-01', to: '2026-10-03', count: 12, runningTotal: 12 },
    { from: '2026-10-04', to: '2026-10-04', count: 28, runningTotal: 40 },
  ],
  reach: { level: 'COUNTY', covered: 3, total: 14, outside: 10, rows: [
    { label: 'Los Angeles (CA)', count: 22 }, { label: 'Kern (CA)', count: null }, { label: 'Inyo (CA)', count: null },
  ] },
  engagement: { changedAnswers: 5, commented: 3 },
}
const election = (avery: number, jordan: number, suppressed = false) => ({
  title: 'City election', totalRespondents: avery + jordan, suppressed, suppressionMessage: null,
  perCandidate: [
    { candidateId: 1, name: 'Avery Stone', affiliation: 'Ind', officeName: 'Mayor', yes: avery, no: jordan, total: avery + jordan },
    { candidateId: 2, name: 'Jordan Reyes', affiliation: 'Civic', officeName: 'Mayor', yes: jordan, no: avery, total: avery + jordan },
  ],
})

function mockApi(report: unknown, outsideSuppressed = false) {
  vi.mocked(axios.get).mockImplementation(async (url: string, cfg?: any) => {
    if (url.endsWith('/report')) return { data: report }
    if (cfg?.params?.outsidePurview === 'false') return { data: election(20, 10) }
    if (cfg?.params?.withinPurview === 'false') return { data: election(4, 6, outsideSuppressed) }
    return { data: election(24, 16) }
  })
}

afterEach(() => vi.resetAllMocks())

describe('PollReportView', () => {
  it('shows totals, timeline, reach (withheld small areas), highlights and the inside/outside split', async () => {
    mockApi(REPORT, true)
    const w = mount(PollReportView)
    await flushPromises()

    expect(w.find('h1').text()).toBe('City election — report')
    const summary = w.find('[data-test="summary"]').text()
    expect(summary).toContain('40')
    expect(summary).toContain('75%') // 30 of 40 in the area
    expect(summary).toContain('5')

    expect(w.findAll('[data-test="timeline"] tbody tr')).toHaveLength(2)
    const reach = w.find('[data-test="reach"]').text()
    expect(reach).toContain('Responses from 3 of 14 counties')
    expect(reach).toContain('Los Angeles (CA)22')
    expect(reach).toContain('Kern (CA)fewer than 10')

    expect(w.find('[data-test="highlights"]').text()).toContain('Avery Stone leads with 24 votes, ahead by 8')

    const split = w.find('[data-test="split"]').text()
    expect(split).toContain('20 (67%)')       // inside
    expect(split).toContain('withheld')       // outside group too small
    expect(w.find('[data-test="too-few"]').exists()).toBe(false)
  })

  it('a small poll shows only its totals', async () => {
    mockApi({ respondents: 4, inArea: null, threshold: 10, timeline: null, reach: null, engagement: null })
    const w = mount(PollReportView)
    await flushPromises()
    expect(w.find('[data-test="too-few"]').text()).toContain('at least 10 people')
    expect(w.find('[data-test="timeline"]').exists()).toBe(false)
    expect(w.find('[data-test="reach"]').exists()).toBe(false)
    expect(w.find('[data-test="summary"]').text()).toContain('—')
  })
})
