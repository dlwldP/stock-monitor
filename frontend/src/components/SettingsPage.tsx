import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { SettingsStatus } from '../types'

interface Row {
  label: string
  envVar: string
  set: boolean
}

function StatusBadge({ set }: { set: boolean }) {
  return <span className={set ? 'status-badge status-badge-on' : 'status-badge status-badge-off'}>{set ? '설정됨' : '미설정'}</span>
}

function StatusTable({ rows }: { rows: Row[] }) {
  return (
    <table>
      <thead>
        <tr>
          <th>항목</th>
          <th>환경변수</th>
          <th>상태</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.envVar}>
            <td>{row.label}</td>
            <td>
              <code>{row.envVar}</code>
            </td>
            <td>
              <StatusBadge set={row.set} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

export function SettingsPage() {
  const [status, setStatus] = useState<SettingsStatus | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .getSettingsStatus()
      .then(setStatus)
      .catch((err) => setError(err instanceof Error ? err.message : '설정 상태를 불러오지 못했습니다.'))
  }, [])

  if (error) {
    return (
      <section className="card">
        <h2>설정</h2>
        <p className="error-text">{error}</p>
      </section>
    )
  }

  if (!status) {
    return (
      <section className="card">
        <h2>설정</h2>
        <p className="empty">불러오는 중...</p>
      </section>
    )
  }

  return (
    <>
      <section className="card">
        <h2>설정</h2>
        <p className="muted">
          값은 여기서 바로 바꿀 수 없습니다 — 전부 서버 환경변수로만 관리되고, 어떤 값도 화면에 노출하지 않습니다 (설정 여부만
          표시). 값을 바꾸려면 서버를 띄울 때 환경변수를 설정한 뒤 재시작하세요. 자세한 설명은 레포의 README를 참고하세요.
        </p>
      </section>

      <section className="card">
        <h2>토스증권 API</h2>
        <StatusTable
          rows={[
            { label: 'Client ID', envVar: 'TOSS_CLIENT_ID', set: status.toss.clientIdSet },
            { label: 'Client Secret', envVar: 'TOSS_CLIENT_SECRET', set: status.toss.clientSecretSet },
            { label: '계좌번호 (X-Tossinvest-Account)', envVar: 'TOSS_ACCOUNT_SEQ', set: status.toss.accountSeqSet },
          ]}
        />
        <p className="muted" style={{ marginTop: '0.75rem' }}>
          실연동 사용:{' '}
          <StatusBadge set={status.toss.useRealClient} />
          {!status.toss.useRealClient && ' (지금은 Mock 시세로 동작 중입니다 — TOSS_API_USE_REAL_CLIENT=true로 켜기 전까지)'}
        </p>
      </section>

      <section className="card">
        <h2>알림 채널</h2>
        <StatusTable
          rows={[
            { label: '디스코드 Webhook', envVar: 'DISCORD_WEBHOOK_URL', set: status.notification.discordWebhookSet },
            { label: 'SMTP (이메일 발송)', envVar: 'SMTP_HOST 등', set: status.notification.smtpConfigured },
            { label: '이메일 수신 주소', envVar: 'NOTIFICATION_EMAIL_TO', set: status.notification.emailToSet },
          ]}
        />
      </section>

      <section className="card">
        <h2>일일 요약(다이제스트)</h2>
        <p>
          활성화 여부: <StatusBadge set={status.digest.enabled} /> · 발송 시각(cron): <code>{status.digest.cron}</code>
        </p>
      </section>
    </>
  )
}
