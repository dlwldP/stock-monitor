import type { AlertRule } from '../types'
import { formatDateTime, formatNumber } from '../format'

interface Props {
  rules: AlertRule[]
  onToggleActive: (id: number, active: boolean) => Promise<void>
  onDelete: (id: number) => Promise<void>
}

function conditionText(rule: AlertRule): string {
  const t = formatNumber(rule.thresholdValue)
  switch (rule.conditionType) {
    case 'PRICE_ABOVE':
      return `${t} 이상 도달 시`
    case 'PRICE_BELOW':
      return `${t} 이하 도달 시`
    case 'PCT_CHANGE':
      return `등락률 ±${t}% 이상`
    case 'VOLUME_SPIKE':
      return `거래량 평균 대비 ${t}배 급증`
    case 'WEEK52_HIGH_NEAR':
      return `52주 신고가 ${t}% 이내 근접`
    case 'WEEK52_LOW_NEAR':
      return `52주 신저가 ${t}% 이내 근접`
  }
}

export function AlertRuleList({ rules, onToggleActive, onDelete }: Props) {
  return (
    <section className="card">
      <h2>알림 규칙</h2>
      {rules.length === 0 ? (
        <p className="empty">등록된 알림 규칙이 없습니다.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>종목</th>
              <th>조건</th>
              <th>채널</th>
              <th>마지막 발송</th>
              <th>활성</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {rules.map((rule) => (
              <tr key={rule.id}>
                <td>
                  {rule.symbol} <span className="muted">({rule.market})</span>
                </td>
                <td>{conditionText(rule)}</td>
                <td>{rule.channels.join(', ')}</td>
                <td>{rule.lastTriggeredAt ? formatDateTime(rule.lastTriggeredAt) : '-'}</td>
                <td>
                  <input
                    type="checkbox"
                    checked={rule.active}
                    onChange={(e) => onToggleActive(rule.id, e.target.checked)}
                  />
                </td>
                <td>
                  <button type="button" className="danger" onClick={() => onDelete(rule.id)}>
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
