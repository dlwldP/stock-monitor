import { useState } from 'react'
import type { AlertChannel, AlertConditionType, Market } from '../types'

interface Props {
  symbol: string
  market: Market
  onSubmit: (input: {
    symbol: string
    market: Market
    conditionType: AlertConditionType
    thresholdValue: number
    channels: AlertChannel[]
    cooldownMinutes: number
  }) => Promise<void>
  onClose: () => void
}

const CHANNEL_OPTIONS: { value: AlertChannel; label: string }[] = [
  { value: 'DISCORD', label: '디스코드' },
  { value: 'EMAIL', label: '이메일' },
  { value: 'INAPP', label: '인앱' },
]

const CONDITION_OPTIONS: {
  value: AlertConditionType
  label: string
  thresholdLabel: string
  placeholder: string
}[] = [
  { value: 'PRICE_BELOW', label: '목표가 이하로 하락', thresholdLabel: '목표가', placeholder: '예: 70000' },
  { value: 'PRICE_ABOVE', label: '목표가 이상으로 상승', thresholdLabel: '목표가', placeholder: '예: 80000' },
  { value: 'PCT_CHANGE', label: '등락률 ±N% 이상', thresholdLabel: '등락률(%)', placeholder: '예: 5' },
  { value: 'VOLUME_SPIKE', label: '거래량 평균 대비 N배 급증', thresholdLabel: '배수', placeholder: '예: 2' },
  { value: 'WEEK52_HIGH_NEAR', label: '52주 신고가 근접', thresholdLabel: '근접 범위(%)', placeholder: '예: 3' },
  { value: 'WEEK52_LOW_NEAR', label: '52주 신저가 근접', thresholdLabel: '근접 범위(%)', placeholder: '예: 3' },
]

export function AlertRuleForm({ symbol, market, onSubmit, onClose }: Props) {
  const [conditionType, setConditionType] = useState<AlertConditionType>('PRICE_BELOW')
  const [thresholdValue, setThresholdValue] = useState('')
  const [channels, setChannels] = useState<AlertChannel[]>(['INAPP'])
  const [cooldownMinutes, setCooldownMinutes] = useState('60')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const selectedCondition = CONDITION_OPTIONS.find((c) => c.value === conditionType) ?? CONDITION_OPTIONS[0]

  function toggleChannel(channel: AlertChannel) {
    setChannels((prev) => (prev.includes(channel) ? prev.filter((c) => c !== channel) : [...prev, channel]))
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const threshold = Number(thresholdValue)
    if (!thresholdValue || Number.isNaN(threshold) || threshold <= 0) {
      setError(`${selectedCondition.thresholdLabel}을(를) 올바르게 입력하세요.`)
      return
    }
    if (channels.length === 0) {
      setError('알림 채널을 하나 이상 선택하세요.')
      return
    }
    setSubmitting(true)
    setError(null)
    try {
      await onSubmit({
        symbol,
        market,
        conditionType,
        thresholdValue: threshold,
        channels,
        cooldownMinutes: Number(cooldownMinutes) || 0,
      })
      onClose()
    } catch (err) {
      setError(err instanceof Error ? err.message : '알림 규칙 생성에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="card alert-rule-form">
      <div className="card-header">
        <h2>
          알림 규칙 추가 — {symbol} ({market})
        </h2>
        <button type="button" className="link" onClick={onClose}>
          닫기
        </button>
      </div>
      <form onSubmit={handleSubmit}>
        <div className="form-row">
          <label>
            조건
            <select value={conditionType} onChange={(e) => setConditionType(e.target.value as AlertConditionType)}>
              {CONDITION_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </label>
          <label>
            {selectedCondition.thresholdLabel}
            <input
              type="number"
              min="0"
              step="0.01"
              value={thresholdValue}
              onChange={(e) => setThresholdValue(e.target.value)}
              placeholder={selectedCondition.placeholder}
            />
          </label>
          <label>
            쿨다운(분)
            <input
              type="number"
              min="0"
              value={cooldownMinutes}
              onChange={(e) => setCooldownMinutes(e.target.value)}
            />
          </label>
        </div>
        <div className="form-row">
          <span className="form-label">알림 채널</span>
          {CHANNEL_OPTIONS.map((opt) => (
            <label key={opt.value} className="checkbox-label">
              <input
                type="checkbox"
                checked={channels.includes(opt.value)}
                onChange={() => toggleChannel(opt.value)}
              />
              {opt.label}
            </label>
          ))}
        </div>
        {error && <p className="error-text">{error}</p>}
        <button type="submit" disabled={submitting}>
          저장
        </button>
      </form>
    </section>
  )
}
