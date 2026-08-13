export type Market = 'KR' | 'US'
export type AlertConditionType =
  | 'PRICE_ABOVE'
  | 'PRICE_BELOW'
  | 'PCT_CHANGE'
  | 'VOLUME_SPIKE'
  | 'WEEK52_HIGH_NEAR'
  | 'WEEK52_LOW_NEAR'
export type AlertChannel = 'DISCORD' | 'INAPP' | 'EMAIL'
export type AlertLogStatus = 'SUCCESS' | 'FAILED'

export interface WatchlistItem {
  id: number
  symbol: string
  market: Market
  displayName: string | null
  createdAt: string
  currentPrice: number
  changeRate: number
}

export interface AlertRule {
  id: number
  symbol: string
  market: Market
  conditionType: AlertConditionType
  thresholdValue: number
  channels: AlertChannel[]
  active: boolean
  cooldownMinutes: number
  lastTriggeredAt: string | null
  createdAt: string
}

export interface AlertLog {
  id: number
  alertRuleId: number
  symbol: string
  triggeredAt: string
  channel: AlertChannel
  status: AlertLogStatus
  message: string
}

export interface Holding {
  symbol: string
  market: Market
  name: string
  quantity: number
  avgPrice: number
  currentPrice: number
  evalAmount: number
  pnl: number
  pnlRate: number
}

export interface AccountSummary {
  totalValue: number
  dailyPnl: number
  dailyPnlRate: number
}

export interface DashboardResponse {
  accountSummary: AccountSummary
  holdings: Holding[]
}

export interface ApiErrorBody {
  timestamp: string
  message: string
  details?: Record<string, string>
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
