import type { PendingOrder } from '../types'
import { formatDateTime, formatNumber } from '../format'

interface Props {
  orders: PendingOrder[]
  loading?: boolean
  /** Set when the orders lookup failed; the rest of the dashboard is unaffected. */
  error?: string | null
}

function statusLabel(order: PendingOrder): string {
  return order.partiallyFilled
    ? `부분체결 (${formatNumber(order.filledQuantity)}/${formatNumber(order.quantity)})`
    : '대기'
}

export function PendingOrdersTable({ orders, loading, error }: Props) {
  return (
    <section className="card">
      <div className="card-header">
        <h2>미체결 주문</h2>
        <span className="muted">주문했지만 아직 체결되지 않은 건</span>
      </div>
      {error ? (
        <p className="error-text">{error}</p>
      ) : orders.length === 0 ? (
        <p className="empty">{loading ? '불러오는 중...' : '미체결 주문이 없습니다.'}</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>종목</th>
              <th>구분</th>
              <th>주문수량</th>
              <th>미체결수량</th>
              <th>주문가</th>
              <th>상태</th>
              <th>주문시각</th>
            </tr>
          </thead>
          <tbody>
            {orders.map((o) => (
              <tr key={o.orderId}>
                <td>
                  {o.name} <span className="muted">({o.symbol})</span>
                </td>
                <td className={o.side === 'BUY' ? 'pnl-positive' : 'pnl-negative'}>
                  {o.side === 'BUY' ? '매수' : '매도'}
                </td>
                <td>{formatNumber(o.quantity)}</td>
                <td>{formatNumber(o.remainingQuantity)}</td>
                <td>{o.price == null ? '시장가' : formatNumber(o.price)}</td>
                <td>{statusLabel(o)}</td>
                <td className="muted">{formatDateTime(o.orderedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  )
}
