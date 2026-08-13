import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { AlertChannel, AlertLog, AlertLogStatus } from '../types'
import { formatDateTime } from '../format'

const CHANNEL_FILTERS: { value: AlertChannel | ''; label: string }[] = [
  { value: '', label: '전체 채널' },
  { value: 'DISCORD', label: '디스코드' },
  { value: 'EMAIL', label: '이메일' },
  { value: 'INAPP', label: '인앱' },
]

const STATUS_FILTERS: { value: AlertLogStatus | ''; label: string }[] = [
  { value: '', label: '전체 상태' },
  { value: 'SUCCESS', label: '성공' },
  { value: 'FAILED', label: '실패' },
]

const PAGE_SIZE = 20

export function AlertHistoryPage() {
  const [channel, setChannel] = useState<AlertChannel | ''>('')
  const [status, setStatus] = useState<AlertLogStatus | ''>('')
  const [page, setPage] = useState(0)
  const [logs, setLogs] = useState<AlertLog[]>([])
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    api
      .getAlertLogsPage({ channel: channel || undefined, status: status || undefined, page, size: PAGE_SIZE })
      .then((res) => {
        if (cancelled) return
        setLogs(res.content)
        setTotalPages(res.totalPages)
        setTotalElements(res.totalElements)
      })
      .catch((err) => {
        if (cancelled) return
        setError(err instanceof Error ? err.message : '알림 히스토리를 불러오지 못했습니다.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [channel, status, page])

  function handleChannelChange(value: AlertChannel | '') {
    setChannel(value)
    setPage(0)
  }

  function handleStatusChange(value: AlertLogStatus | '') {
    setStatus(value)
    setPage(0)
  }

  return (
    <section className="card">
      <h2>알림 히스토리</h2>

      <div className="inline-form">
        <select value={channel} onChange={(e) => handleChannelChange(e.target.value as AlertChannel | '')}>
          {CHANNEL_FILTERS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
        <select value={status} onChange={(e) => handleStatusChange(e.target.value as AlertLogStatus | '')}>
          {STATUS_FILTERS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
        <span className="muted">총 {totalElements}건</span>
      </div>

      {error && <p className="error-text">{error}</p>}

      {!loading && logs.length === 0 ? (
        <p className="empty">조건에 맞는 알림이 없습니다.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>시각</th>
              <th>종목</th>
              <th>채널</th>
              <th>상태</th>
              <th>내용</th>
            </tr>
          </thead>
          <tbody>
            {logs.map((log) => (
              <tr key={log.id}>
                <td className="muted">{formatDateTime(log.triggeredAt)}</td>
                <td>{log.symbol}</td>
                <td>{log.channel}</td>
                <td className={log.status === 'FAILED' ? 'log-failed' : ''}>{log.status}</td>
                <td>{log.message}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {totalPages > 1 && (
        <div className="pagination">
          <button type="button" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>
            이전
          </button>
          <span className="muted">
            {page + 1} / {totalPages}
          </span>
          <button type="button" disabled={page >= totalPages - 1} onClick={() => setPage((p) => p + 1)}>
            다음
          </button>
        </div>
      )}
    </section>
  )
}
