import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import axios from 'axios'
import en from '@/i18n/en.json'
import CreatorRequestsView from './CreatorRequestsView.vue'

vi.mock('axios')

const ROWS = [
  { id: 61, userEmail: 'newbie@test.local', regionLabel: 'California', reason: 'I run the PTA', submittedAt: '2026-10-27T00:00:00Z', status: 'PENDING', assignedAdminId: null, pollTypeIds: [1] },
  { id: 62, userEmail: 'second@test.local', regionLabel: 'Colorado', reason: '', submittedAt: '2026-10-27T00:00:00Z', status: 'PENDING', assignedAdminId: 3, pollTypeIds: [1] },
]

afterEach(() => vi.resetAllMocks())

describe('CreatorRequestsView: cards on phones (2026-10-28)', () => {
  it('a card per request; ticking one card and Approve sends just that request', async () => {
    vi.mocked(axios.get).mockResolvedValue({ data: ROWS })
    vi.mocked(axios.post).mockResolvedValue({ data: {} })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    const i18n = createI18n({ legacy: false, locale: 'en', messages: { en } })
    const w = mount(CreatorRequestsView, { global: { plugins: [i18n] } })
    await flushPromises()

    const cards = w.findAll('[data-test="request-cards"] li')
    expect(cards).toHaveLength(2)
    expect(cards[0].text()).toContain('newbie@test.local')
    expect(cards[0].text()).toContain('I run the PTA')
    expect(cards[0].text()).toContain(en.admin.creatorRequests.badgeUnassigned)
    expect(cards[1].text()).not.toContain(en.admin.creatorRequests.badgeUnassigned)

    await cards[0].find('[data-test="card-select"]').setValue(true)
    await w.findAll('button').find(b => b.text().startsWith(en.admin.creatorRequests.approveSelected.split('{')[0].trim()))!.trigger('click')
    await flushPromises()
    expect(axios.post).toHaveBeenCalledWith('/api/admin/creator-requests/batch-approve', expect.objectContaining({ requestIds: [61] }))
  })
})
