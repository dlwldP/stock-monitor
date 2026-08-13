import type { AccountSnapshot } from '../types'
import { formatDateTime, formatNumber } from '../format'

interface Props {
  snapshots: AccountSnapshot[]
  width?: number
  height?: number
}

const LINE_COLOR = '#3B82F6'
const MARGIN = { top: 10, right: 10, bottom: 20, left: 70 }

export function AssetTrendChart({ snapshots, width = 880, height = 220 }: Props) {
  if (snapshots.length < 2) {
    return (
      <p className="empty">
        추이를 그리기에 데이터가 부족합니다. 스케줄러가 자산 스냅샷을 쌓을 때까지 기다려주세요 (기본 15분 주기).
      </p>
    )
  }

  const innerWidth = width - MARGIN.left - MARGIN.right
  const innerHeight = height - MARGIN.top - MARGIN.bottom

  const values = snapshots.map((s) => s.totalValue)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const pad = (max - min) * 0.1 || 1
  const yMin = min - pad
  const yMax = max + pad

  function y(value: number): number {
    return MARGIN.top + innerHeight * (1 - (value - yMin) / (yMax - yMin))
  }

  function x(index: number): number {
    return MARGIN.left + (innerWidth * index) / (snapshots.length - 1)
  }

  const points = snapshots.map((s, i) => `${x(i)},${y(s.totalValue)}`).join(' ')
  const yTicks = 4
  const tickValues = Array.from({ length: yTicks + 1 }, (_, i) => yMin + ((yMax - yMin) * i) / yTicks)

  const first = snapshots[0]
  const last = snapshots[snapshots.length - 1]

  return (
    <svg viewBox={`0 0 ${width} ${height}`} width="100%" style={{ maxWidth: width, display: 'block' }}>
      {tickValues.map((v) => (
        <g key={v}>
          <line x1={MARGIN.left} x2={width - MARGIN.right} y1={y(v)} y2={y(v)} className="chart-gridline" />
          <text x={MARGIN.left - 8} y={y(v)} textAnchor="end" dominantBaseline="middle" className="chart-axis-label">
            {formatNumber(v)}
          </text>
        </g>
      ))}
      <polyline points={points} fill="none" stroke={LINE_COLOR} strokeWidth={2} />
      {snapshots.map((s, i) => (
        <circle key={s.snapshotAt} cx={x(i)} cy={y(s.totalValue)} r={2.5} fill={LINE_COLOR} />
      ))}
      <text x={MARGIN.left} y={height - 4} textAnchor="start" className="chart-axis-label">
        {formatDateTime(first.snapshotAt)}
      </text>
      <text x={width - MARGIN.right} y={height - 4} textAnchor="end" className="chart-axis-label">
        {formatDateTime(last.snapshotAt)}
      </text>
    </svg>
  )
}
