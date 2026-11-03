/**
 * States with a district map at /maps/XX (public/maps/XX.json, built by
 * scripts/maps/build-state-map.py). Add a state here once its file exists.
 */
export const STATE_MAPS: Record<string, string> = {
  Washington: 'WA',
  Oregon: 'OR',
}

/** The map for a 2026 midterm ballot's state, from its title ("2026 Midterm Washington District 2"). */
export function stateMapFor(title: string | undefined): { name: string; code: string } | null {
  if (!title?.startsWith('2026 Midterm')) return null
  const name = Object.keys(STATE_MAPS).find(n => new RegExp(`^2026 Midterm (· )?${n}( District \\d+|:|$)`).test(title))
  return name ? { name, code: STATE_MAPS[name] } : null
}

export type Party = 'DEM' | 'REP' | 'IND' | 'OTHER'
const PARTY_ORDER: Party[] = ['DEM', 'REP', 'IND', 'OTHER']

/**
 * "top 2" on a state map (decided 2026-11-03): the two parties with the most
 * votes, each party's candidates added up, strongest first. Ties keep the
 * order Dem, Rep, Ind, Other. One entry when only one party ran.
 */
export function topTwoParties(candidates: { party: Party; pct: number | null }[]): [Party, number][] {
  const totals = new Map<Party, number>()
  for (const c of candidates) totals.set(c.party, (totals.get(c.party) ?? 0) + (c.pct ?? 0))
  return [...totals]
    .sort((x, y) => y[1] - x[1] || PARTY_ORDER.indexOf(x[0]) - PARTY_ORDER.indexOf(y[0]))
    .slice(0, 2)
}
