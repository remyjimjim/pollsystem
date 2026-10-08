import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises, RouterLinkStub } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import axios from 'axios'
import ManageCreatorsView from './ManageCreatorsView.vue'

vi.mock('axios')

function grant(over: Record<string, unknown>) {
  return {
    id: 1, scopeLevel: 'STATE', stateId: 5, stateName: 'California', stateInitial: 'CA',
    countyId: null, countyName: null, zipcode: null, pollTypeId: null, pollTypeName: null,
    enabled: true, manageable: true, fromRequest: false, ...over
  }
}
const ROWS = [
  {
    userId: 10, email: 'alice@test.local', enabled: true, canToggle: true, accessState: 'ENABLED', manageable: true, pollCount: 3, pollTotal: 3,
    lastEditedAt: '2026-09-30T12:00:00Z',
    grants: [
      grant({ id: 1 }),
      grant({ id: 2, scopeLevel: 'COUNTY', countyId: 7, countyName: 'Los Angeles' }),
      grant({ id: 3, scopeLevel: 'COUNTY', countyId: 8, countyName: 'Orange', enabled: false })
    ]
  },
  {
    userId: 11, email: 'bob@test.local', enabled: false, canToggle: true, accessState: 'ENABLED', manageable: true, pollCount: 1, pollTotal: 2,
    lastEditedAt: null, grants: [grant({ id: 4, scopeLevel: 'ZIP', zipcode: '90001' })]
  },
  {
    userId: 12, email: 'nat@test.local', enabled: true, canToggle: false, accessState: 'ENABLED', manageable: false, pollCount: 0, pollTotal: 0,
    lastEditedAt: null, grants: [grant({ id: 5, scopeLevel: 'NATIONAL', manageable: false })]
  }
]

beforeEach(() => {
  vi.mocked(axios.get).mockImplementation(async (url: string) => {
    if (url === '/api/admin/creators') return { data: structuredClone(ROWS) }
    if (url === '/api/poll-types') return { data: [] }
    return { data: [] }
  })
  vi.mocked(axios.put).mockImplementation(async (url: string, body: any) => {
    const id = Number(url.split('/')[4])
    const row = structuredClone(ROWS.find(r => r.userId === id)!)
    return { data: { ...row, enabled: body.enabled } }
  })
})
afterEach(() => vi.resetAllMocks())

async function mountView() {
  const w = mount(ManageCreatorsView, {
    attachTo: document.body,
    global: {
      plugins: [createTestingPinia({ createSpy: vi.fn })],
      stubs: { 'router-link': RouterLinkStub, PurviewSetter: true }
    }
  })
  await flushPromises()
  return w
}
const row = (w: ReturnType<typeof mount>, email: string) =>
  w.findAll('[data-test="creator-row"]').find(r => r.text().includes(email))!

describe('ManageCreatorsView', () => {
  it('lists creators with a purview summary, enabled-poll count and Yes / No', async () => {
    const w = await mountView()
    expect(w.findAll('[data-test="creator-row"]')).toHaveLength(3)

    const alice = row(w, 'alice@test.local')
    expect(alice.text()).toContain('CA · 2 counties')
    expect(alice.text()).toContain('Yes')
    expect(row(w, 'bob@test.local').text()).toContain('No')
    expect(row(w, 'bob@test.local').text()).toContain('1 zip')
    expect(row(w, 'nat@test.local').text()).toContain('Nationwide')
    w.unmount()
  })

  it('pops up the full purview, with disabled grants struck through', async () => {
    const w = await mountView()
    await row(w, 'alice@test.local').find('td:nth-child(2) button').trigger('click')
    const pop = w.find('[role="tooltip"]')
    expect(pop.text()).toContain('California — All poll types')
    expect(pop.text()).toContain('Los Angeles (CA)')
    expect(pop.find('li.line-through').text()).toContain('Orange (CA)')
    w.unmount()
  })

  it('links the poll count to Manage Polls filtered to the creator', async () => {
    const w = await mountView()
    const link = row(w, 'alice@test.local').findComponent(RouterLinkStub)
    expect(link.props('to')).toEqual({
      path: '/admin/manage-polls',
      query: { creator: 'alice@test.local', sort: 'closeDate', dir: 'desc', showDisabled: '1' }
    })
    expect(row(w, 'nat@test.local').findComponent(RouterLinkStub).exists()).toBe(false) // 0 polls: no link
    w.unmount()
  })

  it('Enabled is the stored creator switch: uncheck disables, check re-enables, greyed without access', async () => {
    const w = await mountView()
    const box = (email: string) => row(w, email).find('input[type="checkbox"]')

    expect((box('alice@test.local').element as HTMLInputElement).checked).toBe(true)
    await box('alice@test.local').trigger('click')
    await flushPromises()
    expect(axios.put).toHaveBeenLastCalledWith('/api/admin/creators/10/enabled', { enabled: false })
    expect((box('alice@test.local').element as HTMLInputElement).checked).toBe(false)

    expect((box('bob@test.local').element as HTMLInputElement).checked).toBe(false)
    await box('bob@test.local').trigger('click')
    await flushPromises()
    expect(axios.put).toHaveBeenLastCalledWith('/api/admin/creators/11/enabled', { enabled: true })

    const nat = box('nat@test.local')
    expect((nat.element as HTMLInputElement).disabled).toBe(true)
    await nat.trigger('click')
    expect(axios.put).toHaveBeenCalledTimes(2)
    w.unmount()
  })

  it('shows the enabled-poll count but links whenever disabled polls exist too', async () => {
    const w = await mountView()
    const bob = row(w, 'bob@test.local').findComponent(RouterLinkStub)
    expect(bob.exists()).toBe(true)
    expect(bob.text()).toBe('1') // 1 enabled of 2
    w.unmount()
  })

  it('lists you via your admin access: tagged, box locked, admin grants read-only', async () => {
    const me = {
      userId: 1, email: 'me@test.local', enabled: true, canToggle: false, isYou: true, accessState: 'ENABLED',
      manageable: false, pollCount: 0, pollTotal: 0, lastEditedAt: null,
      grants: [grant({ id: 9, role: 'ADMIN', manageable: false, scopeLevel: 'ZIP', zipcode: '90001' })],
    }
    vi.mocked(axios.get).mockImplementation(async (url: string) =>
      ({ data: url === '/api/admin/creators' ? [me] : [] }))
    const w = await mountView()
    const r = row(w, 'me@test.local')
    expect(r.text()).toContain('(you)')
    const box = r.find('input[type="checkbox"]')
    expect(box.attributes('disabled')).toBeDefined()
    expect(r.find('label').attributes('title')).toBe("You can't disable yourself.")

    await r.findAll('button').find(b => b.text() === 'Edit')!.trigger('click')
    const dialog = w.find('[role="dialog"]')
    expect(dialog.text()).toContain('Admin access')
    expect(dialog.text()).not.toContain('Outside your purview')
    expect(dialog.find('input[type="checkbox"]').attributes('disabled')).toBeDefined()
    w.unmount()
  })
})
