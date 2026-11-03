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
