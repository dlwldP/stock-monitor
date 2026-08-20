import type { Holding } from '../types'
import { formatNumber, pnlClass } from '../format'

export function HoldingsTable({ holdings, loading }: { holdings: Holding[]; loading?: boolean }) {
  return (
    <section className="card">
      <h2>보유종목</h2>
      {holdings.length === 0 ? (
        <p className="empty">{loading ? '불러오는 중...' : '보유종목이 없습니다.'}</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>종목</th>
              <th>수량</th>
              <th>평균단가</th>
              <th>현재가</th>
              <th>평가금액</th>
              <th>손익</th>
              <th>손익률</th>
            </tr>
          </thead>
          <tbody>
            {holdings.map((h) => (
              <tr key={`${h.market}-${h.symbol}`}>
                <td>
                  {h.name} <span className="muted">({h.symbol})</span>
                </td>
                <td>{formatNumber(h.quantity)}</td>
                <td>{formatNumber(h.avgPrice)}</td>
                <td>{formatNumber(h.currentPrice)}</td>
                <td>{formatNumber(h.evalAmount)}</td>
                <td className={pnlClass(h.pnl)}>{formatNumber(h.pnl, { signed: true })}</td>
                <td className={pnlClass(h.pnlRate)}>{formatNumber(h.pnlRate, { signed: true })}%</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
