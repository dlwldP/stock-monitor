import { useState } from 'react'
import type { AlertChannel, AlertRule, AlertTriggerMode } from '../types'
import { formatDateTime, formatNumber } from '../format'

export interface AlertRuleEdit {
  thresholdValue: number
  channels: AlertChannel[]
  cooldownMinutes: number
  triggerMode: AlertTriggerMode
}

interface Props {
  rules: AlertRule[]
  loading?: boolean
  onToggleActive: (id: number, active: boolean) => Promise<void>
  onDelete: (id: number) => Promise<void>
  onEdit: (id: number, edit: AlertRuleEdit) => Promise<void>
}

const CHANNEL_OPTIONS: AlertChannel[] = ['DISCORD', 'EMAIL', 'INAPP']

function conditionLabel(rule: AlertRule): string {
  switch (rule.conditionType) {
    case 'PRICE_ABOVE':
      return '이상 도달 시'
    case 'PRICE_BELOW':
      return '이하 도달 시'
    case 'PCT_CHANGE':
      return '% 이상 등락 시'
    case 'VOLUME_SPIKE':
      return '배 이상 거래량 급증 시'
    case 'WEEK52_HIGH_NEAR':
      return '% 이내로 52주 신고가 근접 시'
    case 'WEEK52_LOW_NEAR':
      return '% 이내로 52주 신저가 근접 시'
  }
}

function conditionText(rule: AlertRule): string {
  return `${formatNumber(rule.thresholdValue)} ${conditionLabel(rule)}`
}

/**
 * Row-in-place editing rather than a separate modal/page: the fields worth changing on an
 * existing rule (threshold/channels/cooldown/repeat mode) are exactly the columns already
 * shown in the table, so editing them where they sit avoids re-explaining the row elsewhere.
 * Symbol/market/condition type aren't editable here — see AlertRuleUpdateRequest on the backend
 * for why (changing which condition a rule watches is close enough to "a different rule").
 */
function EditRow({
  rule,
  onSave,
  onCancel,
}: {
  rule: AlertRule
  onSave: (edit: AlertRuleEdit) => Promise<void>
  onCancel: () => void
}) {
  const [thresholdValue, setThresholdValue] = useState(String(rule.thresholdValue))
  const [channels, setChannels] = useState<AlertChannel[]>(rule.channels)
  const [cooldownMinutes, setCooldownMinutes] = useState(String(rule.cooldownMinutes))
  const [triggerMode, setTriggerMode] = useState<AlertTriggerMode>(rule.triggerMode)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function toggleChannel(channel: AlertChannel) {
    setChannels((prev) => (prev.includes(channel) ? prev.filter((c) => c !== channel) : [...prev, channel]))
  }

  async function handleSave() {
    const threshold = Number(thresholdValue)
    if (!thresholdValue || Number.isNaN(threshold) || threshold <= 0) {
      setError('값을 올바르게 입력하세요.')
      return
    }
    if (channels.length === 0) {
      setError('채널을 하나 이상 선택하세요.')
      return
    }
    setSaving(true)
    setError(null)
    try {
      await onSave({ thresholdValue: threshold, channels, cooldownMinutes: Number(cooldownMinutes) || 0, triggerMode })
    } catch (err) {
      setError(err instanceof Error ? err.message : '수정에 실패했습니다.')
      setSaving(false)
    }
  }

  return (
    <tr className="alert-rule-edit-row">
      <td>
        {rule.symbol} <span className="muted">({rule.market})</span>
      </td>
      <td colSpan={2}>
        <input
          type="number"
          min="0"
          step="0.01"
          value={thresholdValue}
          onChange={(e) => setThresholdValue(e.target.value)}
          style={{ width: '90px' }}
        />{' '}
        <select value={triggerMode} onChange={(e) => setTriggerMode(e.target.value as AlertTriggerMode)}>
          <option value="EDGE">1회</option>
          <option value="REPEAT">반복</option>
        </select>{' '}
        쿨다운
        <input
          type="number"
          min="0"
          value={cooldownMinutes}
          onChange={(e) => setCooldownMinutes(e.target.value)}
          style={{ width: '60px' }}
        />
        분
      </td>
      <td>
        {CHANNEL_OPTIONS.map((c) => (
          <label key={c} className="checkbox-label" style={{ marginRight: '6px' }}>
            <input type="checkbox" checked={channels.includes(c)} onChange={() => toggleChannel(c)} />
            {c}
          </label>
        ))}
      </td>
      <td colSpan={2}>
        {error && <span className="error-text">{error}</span>}
      </td>
      <td className="row-actions">
        <button type="button" onClick={handleSave} disabled={saving}>
          저장
        </button>
        <button type="button" className="link" onClick={onCancel} disabled={saving}>
          취소
        </button>
      </td>
    </tr>
  )
}

export function AlertRuleList({ rules, loading, onToggleActive, onDelete, onEdit }: Props) {
  const [editingId, setEditingId] = useState<number | null>(null)

  return (
    <section className="card">
      <h2>알림 규칙</h2>
      {rules.length === 0 ? (
        <p className="empty">{loading ? '불러오는 중...' : '등록된 알림 규칙이 없습니다.'}</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>종목</th>
              <th>조건</th>
              <th>반복</th>
              <th>채널</th>
              <th>마지막 발송</th>
              <th>활성</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {rules.map((rule) =>
              editingId === rule.id ? (
                <EditRow
                  key={rule.id}
                  rule={rule}
                  onCancel={() => setEditingId(null)}
                  onSave={async (edit) => {
                    await onEdit(rule.id, edit)
                    setEditingId(null)
                  }}
                />
              ) : (
                <tr key={rule.id}>
                  <td>
                    {rule.symbol} <span className="muted">({rule.market})</span>
                  </td>
                  <td>{conditionText(rule)}</td>
                  <td className="muted">{rule.triggerMode === 'REPEAT' ? '반복' : '1회'}</td>
                  <td>{rule.channels.join(', ')}</td>
                  <td>{rule.lastTriggeredAt ? formatDateTime(rule.lastTriggeredAt) : '-'}</td>
                  <td>
                    <input
                      type="checkbox"
                      checked={rule.active}
                      onChange={(e) => onToggleActive(rule.id, e.target.checked)}
                    />
                  </td>
                  <td className="row-actions">
                    <button type="button" className="link" onClick={() => setEditingId(rule.id)}>
                      수정
                    </button>
                    <button
                      type="button"
                      className="danger"
                      onClick={() => {
                        if (window.confirm(`${rule.symbol} 알림 규칙을 삭제할까요?`)) {
                          onDelete(rule.id)
                        }
                      }}
                    >
                      삭제
                    </button>
                  </td>
                </tr>
              ),
            )}
          </tbody>
        </table>
      )}
    </section>
  )
}
