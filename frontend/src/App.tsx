import { useCallback, useEffect, useState } from 'react'
import './App.css'
import { api } from './api/client'
import { AlertLogPreview } from './components/AlertLogPreview'
import { AlertRuleForm } from './components/AlertRuleForm'
import { AlertRuleList } from './components/AlertRuleList'
import { AssetSummaryCard } from './components/AssetSummaryCard'
import { HoldingsTable } from './components/HoldingsTable'
import { WatchlistPanel } from './components/WatchlistPanel'
import type { AccountSummary, AlertLog, AlertRule, Holding, Market, WatchlistItem } from './types'

const REFRESH_INTERVAL_MS = 30_000

type BackendStatus = 'checking' | 'ok' | 'unreachable'

function App() {
  const [backendStatus, setBackendStatus] = useState<BackendStatus>('checking')
  const [accountSummary, setAccountSummary] = useState<AccountSummary | null>(null)
  const [holdings, setHoldings] = useState<Holding[]>([])
  const [watchlist, setWatchlist] = useState<WatchlistItem[]>([])
  const [alertRules, setAlertRules] = useState<AlertRule[]>([])
  const [alertLogs, setAlertLogs] = useState<AlertLog[]>([])
  const [alertFormTarget, setAlertFormTarget] = useState<{ symbol: string; market: Market } | null>(null)
  const [banner, setBanner] = useState<string | null>(null)

  const refresh = useCallback(async () => {
    try {
      const [dashboard, watchlistItems, rules, logs] = await Promise.all([
        api.getDashboard(),
        api.getWatchlist(),
        api.getAlertRules(),
        api.getAlertLogs(10),
      ])
      setAccountSummary(dashboard.accountSummary)
      setHoldings(dashboard.holdings)
      setWatchlist(watchlistItems)
      setAlertRules(rules)
      setAlertLogs(logs)
      setBackendStatus('ok')
    } catch {
      setBackendStatus('unreachable')
    }
  }, [])

  useEffect(() => {
    refresh()
    const interval = setInterval(refresh, REFRESH_INTERVAL_MS)
    return () => clearInterval(interval)
  }, [refresh])

  function flashBanner(message: string) {
    setBanner(message)
    setTimeout(() => setBanner(null), 4000)
  }

  async function handleAddWatchlistItem(symbol: string, market: Market, displayName: string) {
    await api.addWatchlistItem({ symbol, market, displayName: displayName || undefined })
    await refresh()
  }

  async function handleDeleteWatchlistItem(id: number) {
    await api.deleteWatchlistItem(id)
    await refresh()
  }

  async function handleCreateAlertRule(input: Parameters<typeof api.createAlertRule>[0]) {
    await api.createAlertRule(input)
    await refresh()
    flashBanner(`${input.symbol} 알림 규칙이 추가되었습니다.`)
  }

  async function handleToggleAlertRule(id: number, active: boolean) {
    await api.setAlertRuleActive(id, active)
    await refresh()
  }

  async function handleDeleteAlertRule(id: number) {
    await api.deleteAlertRule(id)
    await refresh()
  }

  return (
    <main className="dashboard">
      <header className="dashboard-header">
        <h1>TossWatch</h1>
        <span className={`status status-${backendStatus}`}>
          {backendStatus === 'checking' && 'Backend 확인 중...'}
          {backendStatus === 'ok' && 'Backend 연결됨'}
          {backendStatus === 'unreachable' && 'Backend 연결 안 됨'}
        </span>
      </header>

      {banner && <div className="banner">{banner}</div>}

      {backendStatus === 'unreachable' && (
        <p className="error-text">
          백엔드 서버에 연결할 수 없습니다. <code>cd backend && ./gradlew bootRun</code>으로 먼저 실행하세요.
        </p>
      )}

      {accountSummary && <AssetSummaryCard summary={accountSummary} />}
      <HoldingsTable holdings={holdings} />
      <WatchlistPanel
        items={watchlist}
        onAdd={handleAddWatchlistItem}
        onDelete={handleDeleteWatchlistItem}
        onAddAlertRule={(symbol, market) => setAlertFormTarget({ symbol, market })}
      />

      {alertFormTarget && (
        <AlertRuleForm
          symbol={alertFormTarget.symbol}
          market={alertFormTarget.market}
          onSubmit={handleCreateAlertRule}
          onClose={() => setAlertFormTarget(null)}
        />
      )}

      <AlertRuleList rules={alertRules} onToggleActive={handleToggleAlertRule} onDelete={handleDeleteAlertRule} />
      <AlertLogPreview logs={alertLogs} />
    </main>
  )
}

export default App
