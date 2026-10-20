import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import axios from 'axios'
import ManageUsersView from './ManageUsersView.vue'

vi.mock('axios')

const ROW = {
  id: 7, email: 'pat@test.local', phone: '+15550000007', access: 'USER', isEnabled: true,
  zipcode: '90001', stateInitial: 'CA', countyName: 'Los Angeles', latestMessage: null,
  pollsCreatedCount: 0, pollsCompletedCount: 0,
}
const STATES = [{ id: 5, name: 'California', initial: 'CA' }, { id: 6, name: 'Nevada', initial: 'NV' }]

async function mountView() {
  vi.mocked(axios.get).mockImplementation(async (url: string) => {
    if (url === '/api/super/users') return { data: [structuredClone(ROW)] }
    if (url === '/api/states') return { data: STATES }
    return { data: [] }
  })
  vi.mocked(axios.post).mockImplementation(async () => ({ data: { ...ROW, access: 'CREATOR' } }))
  const w = mount(ManageUsersView, { attachTo: document.body })
  await flushPromises()
  return w
}

afterEach(() => { vi.resetAllMocks(); document.body.innerHTML = '' })

describe('ManageUsersView: raising a role grants an area', () => {
  it('Promote asks for the area (states or nationwide) and sends it', async () => {
    const w = await mountView()
    await w.find('button[aria-label="Role actions"]').trigger('click')
    await w.findAll('button').find(b => b.text() === 'Promote to CREATOR')!.trigger('click')

    const dialog = w.find('[data-test="promote-dialog"]')
    expect(dialog.text()).toContain('creator access')
    const levels = dialog.findAll('input[type="radio"][name="scopeLevel"]').map(r => (r.element as HTMLInputElement).value)
    expect(levels).toEqual(['NATIONAL', 'STATE'])
    const go = () => dialog.findAll('button').find(b => b.text() === 'Promote to CREATOR')!
    expect(go().attributes('disabled')).toBeDefined() // no area yet

    await dialog.find('input[type="radio"][value="NATIONAL"]').setValue(true)
    expect(go().attributes('disabled')).toBeUndefined()
    await go().trigger('click')
    await flushPromises()
    expect(axios.post).toHaveBeenCalledWith('/api/super/users/7/promote', { scopeLevel: 'NATIONAL', regionIds: [] })
    expect(w.find('[data-test="promote-dialog"]').exists()).toBe(false)
  })

  it('Edit shows the area picker only when the role goes up', async () => {
    const w = await mountView()
    await w.findAll('button').find(b => b.text() === 'USER')!.trigger('click') // the role badge opens Edit
    await flushPromises()
    expect(w.find('[data-test="edit-area"]').exists()).toBe(false)

    await w.find('select').setValue('ADMIN')
    const area = w.find('[data-test="edit-area"]')
    expect(area.text()).toContain('admin access')
    const save = w.findAll('button').find(b => b.text() === 'Save')!
    expect(save.attributes('disabled')).toBeDefined()
    await area.find('input[type="radio"][value="NATIONAL"]').setValue(true)
    expect(save.attributes('disabled')).toBeUndefined()
  })
})

describe('ManageUsersView: deleting an account on request', () => {
  it('the delete button stays off until the email is typed, then posts it and drops the row', async () => {
    const w = await mountView()
    vi.mocked(axios.post).mockImplementation(async () => ({ data: { email: ROW.email, pollsRecounted: 2, subscriptionCancelled: true } }))
    await w.find('button[aria-label="Role actions"]').trigger('click')
    await w.find('[data-test="delete-account"]').trigger('click')

    const yes = () => w.find('[data-test="delete-yes"]')
    expect(w.find('[data-test="delete-modal"]').text()).toContain('pat@test.local')
    expect(yes().attributes('disabled')).toBeDefined()
    await w.find('[data-test="delete-confirm"]').setValue('someone@else')
    expect(yes().attributes('disabled')).toBeDefined()
    await w.find('[data-test="delete-confirm"]').setValue('PAT@test.local')
    expect(yes().attributes('disabled')).toBeUndefined()

    await yes().trigger('click')
    await flushPromises()
    expect(axios.post).toHaveBeenCalledWith('/api/super/users/7/delete', { confirmEmail: 'PAT@test.local' })
    expect(w.find('[data-test="delete-modal"]').exists()).toBe(false)
    expect(w.text()).not.toContain('+15550000007')
    expect(w.text()).toContain('Deleted pat@test.local and cancelled their subscription. Results recounted for 2 poll(s).')
  })

  it("a refusal (e.g. they own polls) is shown, and the account stays", async () => {
    const w = await mountView()
    vi.mocked(axios.post).mockRejectedValue({ response: { data: { message: "Can't delete yet: this account owns 1 poll." } } })
    await w.find('button[aria-label="Role actions"]').trigger('click')
    await w.find('[data-test="delete-account"]').trigger('click')
    await w.find('[data-test="delete-confirm"]').setValue('pat@test.local')
    await w.find('[data-test="delete-yes"]').trigger('click')
    await flushPromises()
    expect(w.find('[data-test="delete-error"]').text()).toBe("Can't delete yet: this account owns 1 poll.")
    expect(w.text()).toContain('pat@test.local')
  })

  it('ticking Lapsed asks for VIEWER accounts too', async () => {
    vi.useFakeTimers()
    try {
      const w = await mountView()
      vi.mocked(axios.get).mockClear()
      await w.find('[data-test="filter-lapsed"]').setValue(true)
      await vi.runAllTimersAsync()
      const call = vi.mocked(axios.get).mock.calls.find(c => c[0] === '/api/super/users')!
      expect((call[1] as any).params.role).toBe('USER,CREATOR,ADMIN,VIEWER')
    } finally {
      vi.useRealTimers()
    }
  })
})
