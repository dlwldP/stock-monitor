import { useCallback, useEffect, useMemo, useState } from 'react'
import './App.css'
import { api } from './api/client'
import { AlertHistoryPage } from './components/AlertHistoryPage'
import { AlertLogPreview } from './components/AlertLogPreview'
import { AlertRuleForm } from './components/AlertRuleForm'
import { AlertRuleList } from './components/AlertRuleList'
import { AssetSummaryCard } from './components/AssetSummaryCard'
import { AssetTrendChart } from './components/AssetTrendChart'
import { ChartsPage, type ChartSymbolOption } from './components/ChartsPage'
import { HoldingsTable } from './components/HoldingsTable'
import { WatchlistPanel } from './components/WatchlistPanel'
import type { AccountSnapshot, AccountSummary, AlertLog, AlertRule, Holding, Market, WatchlistItem } from './types'

const REFRESH_INTERVAL_MS = 30_000

type BackendStatus = 'checking' | 'ok' | 'unreachable'
type View = 'dashboard' | 'charts' | 'history'

function App() {
  const [view, setView] = useState<View>('dashboard')
  const [backendStatus, setBackendStatus] = useState<BackendStatus>('checking')
  const [hasLoadedOnce, setHasLoadedOnce] = useState(false)
  const [accountSummary, setAccountSummary] = useState<AccountSummary | null>(null)
  const [accountHistory, setAccountHistory] = useState<AccountSnapshot[]>([])
  const [holdings, setHoldings] = useState<Holding[]>([])
  const [watchlist, setWatchlist] = useState<WatchlistItem[]>([])
  const [alertRules, setAlertRules] = useState<AlertRule[]>([])
  const [alertLogs, setAlertLogs] = useState<AlertLog[]>([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [alertFormTarget, setAlertFormTarget] = useState<{ symbol: string; market: Market } | null>(null)
  const [banner, setBanner] = useState<string | null>(null)

  const refreshUnreadCount = useCallback(() => {
    api.getUnreadAlertCount().then((res) => setUnreadCount(res.unread)).catch(() => {})
  }, [])

  const refresh = useCallback(async () => {
    try {
      const [dashboard, history, watchlistItems, rules, logs] = await Promise.all([
        api.getDashboard(),
        api.getAccountHistory(90),
        api.getWatchlist(),
        api.getAlertRules(),
        api.getRecentAlertLogs(10),
      ])
      setAccountSummary(dashboard.accountSummary)
      setHoldings(dashboard.holdings)
      setAccountHistory(history)
      setWatchlist(watchlistItems)
      setAlertRules(rules)
      setAlertLogs(logs)
      setBackendStatus('ok')
      setHasLoadedOnce(true)
      refreshUnreadCount()
    } catch {
      setBackendStatus('unreachable')
    }
  }, [refreshUnreadCount])

  useEffect(() => {
    refresh()
    const interval = setInterval(refresh, REFRESH_INTERVAL_MS)
    return () => clearInterval(interval)
  }, [refresh])

  const chartOptions: ChartSymbolOption[] = useMemo(() => {
    const bySymbol = new Map<string, ChartSymbolOption>()
    for (const h of holdings) {
      bySymbol.set(`${h.market}:${h.symbol}`, { symbol: h.symbol, market: h.market, label: `${h.name} (${h.symbol})` })
    }
    for (const w of watchlist) {
      const key = `${w.market}:${w.symbol}`
      if (!bySymbol.has(key)) {
        bySymbol.set(key, { symbol: w.symbol, market: w.market, label: `${w.displayName || w.symbol} (${w.symbol})` })
      }
    }
    return [...bySymbol.values()]
  }, [holdings, watchlist])

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

  async function handleMarkLogRead(id: number) {
    setAlertLogs((prev) => prev.map((l) => (l.id === id ? { ...l, read: true } : l)))
    try {
      await api.markAlertLogRead(id)
      refreshUnreadCount()
    } catch {
      setAlertLogs((prev) => prev.map((l) => (l.id === id ? { ...l, read: false } : l)))
    }
  }

  return (
    <main className="dashboard">
      <header className="dashboard-header">
        <h1>TossWatch</h1>
        <nav className="view-tabs">
          <button type="button" className={view === 'dashboard' ? 'active' : ''} onClick={() => setView('dashboard')}>
            대시보드
          </button>
          <button type="button" className={view === 'charts' ? 'active' : ''} onClick={() => setView('charts')}>
            차트
          </button>
          <button type="button" className={view === 'history' ? 'active' : ''} onClick={() => setView('history')}>
            알림 히스토리
            {unreadCount > 0 && <span className="nav-badge">{unreadCount > 99 ? '99+' : unreadCount}</span>}
          </button>
        </nav>
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

      {view === 'dashboard' && (
        <>
          {accountSummary && <AssetSummaryCard summary={accountSummary} />}
          <section className="card">
            <h2>자산 추이</h2>
            <AssetTrendChart snapshots={accountHistory} />
          </section>
          <HoldingsTable holdings={holdings} loading={!hasLoadedOnce} />
          <WatchlistPanel
            items={watchlist}
            loading={!hasLoadedOnce}
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

          <AlertRuleList
            rules={alertRules}
            loading={!hasLoadedOnce}
            onToggleActive={handleToggleAlertRule}
            onDelete={handleDeleteAlertRule}
          />
          <AlertLogPreview logs={alertLogs} loading={!hasLoadedOnce} onMarkRead={handleMarkLogRead} />
        </>
      )}

      {view === 'charts' && <ChartsPage options={chartOptions} />}
      {view === 'history' && <AlertHistoryPage onUnreadCountChange={refreshUnreadCount} />}
    </main>
  )
}

export default App
