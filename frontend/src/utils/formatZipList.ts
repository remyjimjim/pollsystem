/**
 * Formats a list of zipcodes for display. When the caller knows the
 * total number of zipcodes available for the underlying geography
 * (e.g. the ZipSetter knows how many zips belong to the picked state),
 * pass it as `totalAvailable` so the "All (first 5, ...)" treatment
 * fires exactly when every available zip is selected. When the total
 * isn't known (admin viewing a submitted request, etc.), the same
 * compact form kicks in whenever the list has more than five entries.
 *
 * Examples:
 *   formatZipList([])                          → ""
 *   formatZipList(["80111","80112"])           → "80111, 80112"
 *   formatZipList(seven)                       → "All (80111, 80112, 80113, 80114, 80115, ...)"
 *   formatZipList(fiveAllSelected, { totalAvailable: 5 })
 *                                              → "All (80111, 80112, 80113, 80114, 80115)"
 *   formatZipList(allOfColorado, { totalAvailable: 500 })
 *                                              → "All (80111, 80112, 80113, 80114, 80115, ...)"
 */
export function formatZipList(
  zips: string[],
  options: { totalAvailable?: number } = {}
): string {
  if (zips.length === 0) return ''
  const isAllAvailable =
    options.totalAvailable != null && zips.length === options.totalAvailable
  if (isAllAvailable || zips.length > 5) {
    const head = zips.slice(0, 5).join(', ')
    const suffix = zips.length > 5 ? ', ...' : ''
    return `All (${head}${suffix})`
  }
  return zips.join(', ')
}
