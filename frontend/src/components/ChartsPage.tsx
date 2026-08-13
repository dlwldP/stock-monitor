import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { Candle, Market } from '../types'
import { CandleChart } from './CandleChart'

export interface ChartSymbolOption {
  symbol: string
  market: Market
  label: string
}

interface Props {
  options: ChartSymbolOption[]
}

const DAYS_CHOICES = [30, 60, 120] as const

export function ChartsPage({ options }: Props) {
  const [selectedKey, setSelectedKey] = useState<string>(options[0] ? keyOf(options[0]) : '')
  const [days, setDays] = useState<(typeof DAYS_CHOICES)[number]>(60)
  const [candles, setCandles] = useState<Candle[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const selected = options.find((o) => keyOf(o) === selectedKey) ?? options[0]

  useEffect(() => {
    if (!selected) return
    setLoading(true)
    setError(null)
    api
      .getCandles(selected.symbol, selected.market, days)
      .then(setCandles)
      .catch((err) => setError(err instanceof Error ? err.message : '차트 데이터를 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [selected, days])

  return (
    <section className="card">
      <h2>캔들 차트</h2>

      {options.length === 0 ? (
        <p className="empty">차트를 보려면 먼저 관심종목을 추가하세요.</p>
      ) : (
        <>
          <div className="chart-controls">
            <select value={selectedKey || keyOf(options[0])} onChange={(e) => setSelectedKey(e.target.value)}>
              {options.map((opt) => (
                <option key={keyOf(opt)} value={keyOf(opt)}>
                  {opt.label}
                </option>
              ))}
            </select>
            <select value={days} onChange={(e) => setDays(Number(e.target.value) as (typeof DAYS_CHOICES)[number])}>
              {DAYS_CHOICES.map((d) => (
                <option key={d} value={d}>
                  {d}일
                </option>
              ))}
            </select>
            {loading && <span className="muted">불러오는 중...</span>}
          </div>

          {error && <p className="error-text">{error}</p>}
          {!error && <CandleChart candles={candles} />}
        </>
      )}
    </section>
  )
}

function keyOf(opt: ChartSymbolOption): string {
  return `${opt.market}:${opt.symbol}`
}
