import { describe, it, expect } from 'vitest'
import { stateMapFor } from './stateMaps'

describe('which results pages link to a state map', () => {
  it("a state's 2026 midterm ballots do: district and statewide", () => {
    expect(stateMapFor('2026 Midterm Washington District 2')).toEqual({ name: 'Washington', code: 'WA' })
    expect(stateMapFor('2026 Midterm · Washington: Supreme Court & statewide measures')).toEqual({ name: 'Washington', code: 'WA' })
  })
  it("others don't: other states, other polls, a look-alike name", () => {
    expect(stateMapFor('2026 Midterm Colorado District 1')).toBeNull()
    expect(stateMapFor('Washington parks questionnaire')).toBeNull()
    expect(stateMapFor('2026 Midterm Washingtonia')).toBeNull()
    expect(stateMapFor(undefined)).toBeNull()
  })
})
