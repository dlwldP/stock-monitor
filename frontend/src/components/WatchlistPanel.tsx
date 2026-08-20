import { useState } from 'react'
import type { Market, WatchlistItem } from '../types'
import { formatNumber, pnlClass } from '../format'

interface Props {
  items: WatchlistItem[]
  loading?: boolean
  onAdd: (symbol: string, market: Market, displayName: string) => Promise<void>
  onDelete: (id: number) => Promise<void>
  onAddAlertRule: (symbol: string, market: Market) => void
}

export function WatchlistPanel({ items, loading, onAdd, onDelete, onAddAlertRule }: Props) {
  const [symbol, setSymbol] = useState('')
  const [market, setMarket] = useState<Market>('KR')
  const [displayName, setDisplayName] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!symbol.trim()) return
    setSubmitting(true)
    setError(null)
    try {
      await onAdd(symbol.trim(), market, displayName.trim())
      setSymbol('')
      setDisplayName('')
    } catch (err) {
      setError(err instanceof Error ? err.message : '추가에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="card">
      <h2>관심종목</h2>

      <form className="inline-form" onSubmit={handleSubmit}>
        <select value={market} onChange={(e) => setMarket(e.target.value as Market)}>
          <option value="KR">KR</option>
          <option value="US">US</option>
        </select>
        <input
          placeholder="종목코드 (예: 005930, AAPL)"
          value={symbol}
          onChange={(e) => setSymbol(e.target.value)}
        />
        <input
          placeholder="표시 이름 (선택)"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
        />
        <button type="submit" disabled={submitting || !symbol.trim()}>
          추가
        </button>
      </form>
      {error && <p className="error-text">{error}</p>}

      {items.length === 0 ? (
        <p className="empty">{loading ? '불러오는 중...' : '관심종목이 없습니다. 위에서 추가해보세요.'}</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>종목</th>
              <th>현재가</th>
              <th>등락률</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {items.map((item) => (
              <tr key={item.id}>
                <td>
                  {item.displayName || item.symbol} <span className="muted">({item.symbol})</span>
                </td>
                <td>{formatNumber(item.currentPrice)}</td>
                <td className={pnlClass(item.changeRate)}>{formatNumber(item.changeRate, { signed: true })}%</td>
                <td className="row-actions">
                  <button type="button" onClick={() => onAddAlertRule(item.symbol, item.market)}>
                    알림 추가
                  </button>
                  <button
                    type="button"
                    className="danger"
                    onClick={() => {
                      if (window.confirm(`${item.displayName || item.symbol}을(를) 관심종목에서 삭제할까요?`)) {
                        onDelete(item.id)
                      }
                    }}
                  >
                    삭제
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
