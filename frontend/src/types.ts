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
  /** null when this item's quote couldn't be fetched — the row is still listed. */
  currentPrice: number | null
  /** null when the data source doesn't provide a change rate (the real Toss price API doesn't). */
  changeRate: number | null
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
  triggerMode: AlertTriggerMode
  lastTriggeredAt: string | null
  createdAt: string
}

/** EDGE: 조건이 충족될 때 한 번만. REPEAT: 조건이 유지되는 동안 쿨다운마다 반복. */
export type AlertTriggerMode = 'EDGE' | 'REPEAT'

export type OrderSide = 'BUY' | 'SELL'

export interface PendingOrder {
  orderId: string
  symbol: string
  name: string
  market: Market
  side: OrderSide
  quantity: number
  filledQuantity: number
  remainingQuantity: number
  partiallyFilled: boolean
  /** null for a market order. */
  price: number | null
  orderedAt: string
}

export interface AlertLog {
  id: number
  alertRuleId: number
  symbol: string
  triggeredAt: string
  channel: AlertChannel
  status: AlertLogStatus
  message: string
  read: boolean
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

export interface Candle {
  date: string
  open: number
  high: number
  low: number
  close: number
  volume: number
}

export interface AccountSnapshot {
  snapshotAt: string
  totalValue: number
  dailyPnl: number
  dailyPnlRate: number
}

export interface SettingsStatus {
  toss: {
    clientIdSet: boolean
    clientSecretSet: boolean
    accountSeqSet: boolean
    useRealClient: boolean
  }
  notification: {
    discordWebhookSet: boolean
    smtpConfigured: boolean
    emailToSet: boolean
  }
  digest: {
    enabled: boolean
    cron: string
  }
}
