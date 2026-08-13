import type {
  AlertLog,
  AlertRule,
  ApiErrorBody,
  DashboardResponse,
  Market,
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

  getAlertLogs: (limit = 10) => request<AlertLog[]>(`/api/alert-logs?limit=${limit}`),
}
