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
  { value: 'INAPP', label: '인앱' },
]

export function AlertRuleForm({ symbol, market, onSubmit, onClose }: Props) {
  const [conditionType, setConditionType] = useState<AlertConditionType>('PRICE_BELOW')
  const [thresholdValue, setThresholdValue] = useState('')
  const [channels, setChannels] = useState<AlertChannel[]>(['INAPP'])
  const [cooldownMinutes, setCooldownMinutes] = useState('60')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function toggleChannel(channel: AlertChannel) {
    setChannels((prev) => (prev.includes(channel) ? prev.filter((c) => c !== channel) : [...prev, channel]))
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const threshold = Number(thresholdValue)
    if (!thresholdValue || Number.isNaN(threshold) || threshold <= 0) {
      setError('목표가를 올바르게 입력하세요.')
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
              <option value="PRICE_BELOW">목표가 이하로 하락</option>
              <option value="PRICE_ABOVE">목표가 이상으로 상승</option>
            </select>
          </label>
          <label>
            목표가
            <input
              type="number"
              min="0"
              step="0.01"
              value={thresholdValue}
              onChange={(e) => setThresholdValue(e.target.value)}
              placeholder="예: 70000"
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
