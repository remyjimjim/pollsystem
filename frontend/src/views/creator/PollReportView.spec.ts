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
// Public results: rounded percentages only (small-poll privacy).
const election = (avery: number, jordan: number, suppressed = false) => ({
  title: 'City election', totalRespondents: null, suppressed, suppressionMessage: null,
  visibility: { mode: 'STEPPED', respondentsSoFar: 40, minimum: 10, step: 10 },
  perCandidate: [
    { candidateId: 1, name: 'Avery Stone', affiliation: 'Ind', officeName: 'Mayor', yes: null, no: null, total: null, yesPct: avery, noPct: 100 - avery },
    { candidateId: 2, name: 'Jordan Reyes', affiliation: 'Civic', officeName: 'Mayor', yes: null, no: null, total: null, yesPct: jordan, noPct: 100 - jordan },
  ],
})

function mockApi(report: unknown, outsideSuppressed = false) {
  vi.mocked(axios.get).mockImplementation(async (url: string, cfg?: any) => {
    if (url.endsWith('/report')) return { data: report }
    if (cfg?.params?.outsidePurview === 'false') return { data: election(67, 33) }
    if (cfg?.params?.withinPurview === 'false') return { data: election(40, 60, outsideSuppressed) }
    return { data: election(60, 40) }
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

    expect(w.find('[data-test="highlights"]').text()).toContain('Avery Stone leads with 60%, ahead by 20 points')

    const split = w.find('[data-test="split"]').text()
    expect(split).toContain('67%')            // inside
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

  it('Download CSV holds what the page shows, with withheld cells kept withheld', async () => {
    mockApi(REPORT, true)
    let blob: Blob | null = null
    const create = vi.fn((b: Blob) => { blob = b; return 'blob:report' })
    Object.assign(URL, { createObjectURL: create, revokeObjectURL: vi.fn() })
    const clicked = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

    const w = mount(PollReportView)
    await flushPromises()
    await w.find('[data-test="download-csv"]').trigger('click')

    expect(clicked).toHaveBeenCalled()
    const csv = await (blob as unknown as Blob).text()
    expect(csv).toContain('Summary,respondents,40')
    expect(csv).toContain('Summary,in the poll\'s area,75%')
    expect(csv).toContain('Reach,Los Angeles (CA),22')
    expect(csv).toContain('Reach,Kern (CA),fewer than 10')           // withheld stays withheld
    expect(csv).toContain('Highlights,Mayor,"Avery Stone leads with 60%, ahead by 20 points"')
    expect(csv).toMatch(/Inside vs\. outside the poll's area,Avery Stone · Mayor,67%,withheld/)
    clicked.mockRestore()
  })

  it('has a Print button', async () => {
    mockApi(REPORT)
    const print = vi.spyOn(window, 'print').mockImplementation(() => {})
    const w = mount(PollReportView)
    await flushPromises()
    await w.find('[data-test="print"]').trigger('click')
    expect(print).toHaveBeenCalled()
    print.mockRestore()
  })
})
