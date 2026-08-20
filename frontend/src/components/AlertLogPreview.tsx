import type { AlertLog } from '../types'
import { formatDateTime } from '../format'

interface Props {
  logs: AlertLog[]
  loading?: boolean
  onMarkRead?: (id: number) => void
}

export function AlertLogPreview({ logs, loading, onMarkRead }: Props) {
  return (
    <section className="card">
      <h2>최근 알림</h2>
      {logs.length === 0 ? (
        <p className="empty">{loading ? '불러오는 중...' : '아직 발송된 알림이 없습니다.'}</p>
      ) : (
        <ul className="log-list">
          {logs.map((log) => (
            <li
              key={log.id}
              className={`${log.status === 'FAILED' ? 'log-failed' : 'log-success'} ${!log.read ? 'log-unread' : ''}`}
              onClick={() => !log.read && onMarkRead?.(log.id)}
            >
              {!log.read && <span className="unread-dot" title="안읽음" />}
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
