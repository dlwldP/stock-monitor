/** Renders '-' for a missing value, so callers can pass fields the API may omit. */
export function formatNumber(value: number | null | undefined, opts: { signed?: boolean } = {}): string {
  if (value == null || Number.isNaN(value)) return '-'
  const formatted = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 2 }).format(Math.abs(value))
  if (!opts.signed) return formatted
  if (value > 0) return `+${formatted}`
  if (value < 0) return `-${formatted}`
  return formatted
}

export function pnlClass(value: number | null | undefined): string {
  if (value == null) return ''
  if (value > 0) return 'pnl-positive'
  if (value < 0) return 'pnl-negative'
  return ''
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR')
}
