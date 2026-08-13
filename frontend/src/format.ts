export function formatNumber(value: number, opts: { signed?: boolean } = {}): string {
  const formatted = new Intl.NumberFormat('ko-KR', { maximumFractionDigits: 2 }).format(Math.abs(value))
  if (!opts.signed) return formatted
  if (value > 0) return `+${formatted}`
  if (value < 0) return `-${formatted}`
  return formatted
}

export function pnlClass(value: number): string {
  if (value > 0) return 'pnl-positive'
  if (value < 0) return 'pnl-negative'
  return ''
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR')
}
