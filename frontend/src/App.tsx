import { useEffect, useState } from 'react'
import './App.css'

type BackendStatus = 'checking' | 'ok' | 'unreachable'

function App() {
  const [backendStatus, setBackendStatus] = useState<BackendStatus>('checking')

  useEffect(() => {
    fetch('/api/health')
      .then((res) => (res.ok ? res.json() : Promise.reject(res.status)))
      .then((data) => setBackendStatus(data.status === 'ok' ? 'ok' : 'unreachable'))
      .catch(() => setBackendStatus('unreachable'))
  }, [])

  return (
    <main className="app-shell">
      <h1>TossWatch</h1>
      <p>관심종목 알림 + 대시보드 (개발 시작 전 스캐폴딩 단계)</p>
      <p>
        Backend:{' '}
        <span className={`status status-${backendStatus}`}>
          {backendStatus === 'checking' && '확인 중...'}
          {backendStatus === 'ok' && '연결됨'}
          {backendStatus === 'unreachable' && '연결 안 됨 (backend 서버를 먼저 실행하세요)'}
        </span>
      </p>
    </main>
  )
}

export default App
