import type {
  AccountSnapshot,
  AlertChannel,
  AlertLog,
  AlertLogStatus,
  AlertRule,
  ApiErrorBody,
  Candle,
  DashboardResponse,
  Market,
  PageResponse,
  SettingsStatus,
  WatchlistItem,
} from '../types'

export class ApiError extends Error {
  details?: Record<string, string>

  constructor(body: ApiErrorBody, status: number) {
    super(body.message ?? `요청이 실패했습니다 (HTTP ${status})`)
    this.details = body.details
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
  if (!res.ok) {
    let body: ApiErrorBody
    try {
      body = await res.json()
    } catch {
      throw new Error(`요청이 실패했습니다 (HTTP ${res.status})`)
    }
    throw new ApiError(body, res.status)
  }
  if (res.status === 204) {
    return undefined as T
  }
  return res.json() as Promise<T>
}

export const api = {
  getDashboard: () => request<DashboardResponse>('/api/dashboard'),
  getAccountHistory: (limit = 90) => request<AccountSnapshot[]>(`/api/dashboard/history?limit=${limit}`),
  getCandles: (symbol: string, market: Market, days = 60) =>
    request<Candle[]>(`/api/candles?symbol=${encodeURIComponent(symbol)}&market=${market}&days=${days}`),

  getWatchlist: () => request<WatchlistItem[]>('/api/watchlist'),
  addWatchlistItem: (body: { symbol: string; market: Market; displayName?: string }) =>
    request<WatchlistItem>('/api/watchlist', { method: 'POST', body: JSON.stringify(body) }),
  deleteWatchlistItem: (id: number) => request<void>(`/api/watchlist/${id}`, { method: 'DELETE' }),

  getAlertRules: () => request<AlertRule[]>('/api/alert-rules'),
  createAlertRule: (body: {
    symbol: string
    market: Market
    conditionType: string
    thresholdValue: number
    channels: string[]
    cooldownMinutes?: number
  }) => request<AlertRule>('/api/alert-rules', { method: 'POST', body: JSON.stringify(body) }),
  setAlertRuleActive: (id: number, active: boolean) =>
    request<AlertRule>(`/api/alert-rules/${id}`, { method: 'PATCH', body: JSON.stringify({ active }) }),
  deleteAlertRule: (id: number) => request<void>(`/api/alert-rules/${id}`, { method: 'DELETE' }),

  getRecentAlertLogs: (limit = 10) => request<AlertLog[]>(`/api/alert-logs/recent?limit=${limit}`),
  getAlertLogsPage: (filter: { channel?: AlertChannel; status?: AlertLogStatus; page?: number; size?: number }) => {
    const params = new URLSearchParams()
    if (filter.channel) params.set('channel', filter.channel)
    if (filter.status) params.set('status', filter.status)
    params.set('page', String(filter.page ?? 0))
    params.set('size', String(filter.size ?? 20))
    return request<PageResponse<AlertLog>>(`/api/alert-logs?${params.toString()}`)
  },
  getUnreadAlertCount: () => request<{ unread: number }>('/api/alert-logs/unread-count'),
  markAlertLogRead: (id: number) => request<AlertLog>(`/api/alert-logs/${id}/read`, { method: 'PATCH' }),
  markAllAlertLogsRead: () => request<void>('/api/alert-logs/mark-all-read', { method: 'POST' }),

  getSettingsStatus: () => request<SettingsStatus>('/api/settings/status'),
}
