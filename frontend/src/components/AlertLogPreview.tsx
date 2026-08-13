import type { AlertLog } from '../types'
import { formatDateTime } from '../format'

export function AlertLogPreview({ logs }: { logs: AlertLog[] }) {
  return (
    <section className="card">
      <h2>최근 알림</h2>
      {logs.length === 0 ? (
        <p className="empty">아직 발송된 알림이 없습니다.</p>
      ) : (
        <ul className="log-list">
          {logs.map((log) => (
            <li key={log.id} className={log.status === 'FAILED' ? 'log-failed' : 'log-success'}>
              <span className="log-time">{formatDateTime(log.triggeredAt)}</span>
              <span className="log-channel">[{log.channel}]</span>
              <span>{log.message}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
