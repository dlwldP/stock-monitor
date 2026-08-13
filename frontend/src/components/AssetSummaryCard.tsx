import type { AccountSummary } from '../types'
import { formatNumber, pnlClass } from '../format'

export function AssetSummaryCard({ summary }: { summary: AccountSummary }) {
  return (
    <section className="card">
      <h2>자산 요약</h2>
      <div className="summary-grid">
        <div>
          <div className="summary-label">총 평가금액</div>
          <div className="summary-value">{formatNumber(summary.totalValue)}</div>
        </div>
        <div>
          <div className="summary-label">당일 손익</div>
          <div className={`summary-value ${pnlClass(summary.dailyPnl)}`}>
            {formatNumber(summary.dailyPnl, { signed: true })}
          </div>
        </div>
        <div>
          <div className="summary-label">손익률</div>
          <div className={`summary-value ${pnlClass(summary.dailyPnlRate)}`}>
            {formatNumber(summary.dailyPnlRate, { signed: true })}%
          </div>
        </div>
      </div>
    </section>
  )
}
