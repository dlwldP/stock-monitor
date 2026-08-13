import type { Candle } from '../types'

interface Props {
  candles: Candle[]
  width?: number
  height?: number
}

const UP_COLOR = '#d0362c' // Korean convention: price up = red
const DOWN_COLOR = '#1a66c4' // price down = blue
const MARGIN = { top: 10, right: 10, bottom: 20, left: 60 }

export function CandleChart({ candles, width = 880, height = 320 }: Props) {
  if (candles.length === 0) {
    return <p className="empty">캔들 데이터가 없습니다.</p>
  }

  const innerWidth = width - MARGIN.left - MARGIN.right
  const innerHeight = height - MARGIN.top - MARGIN.bottom

  const min = Math.min(...candles.map((c) => c.low))
  const max = Math.max(...candles.map((c) => c.high))
  const pad = (max - min) * 0.05 || 1
  const yMin = min - pad
  const yMax = max + pad

  const slotWidth = innerWidth / candles.length
  const bodyWidth = Math.max(1, slotWidth * 0.6)

  function y(price: number): number {
    return MARGIN.top + innerHeight * (1 - (price - yMin) / (yMax - yMin))
  }

  function x(index: number): number {
    return MARGIN.left + slotWidth * index + slotWidth / 2
  }

  const yTicks = 5
  const tickValues = Array.from({ length: yTicks + 1 }, (_, i) => yMin + ((yMax - yMin) * i) / yTicks)

  // Show a date label roughly every 10th candle, plus the last one.
  const labelEvery = Math.max(1, Math.floor(candles.length / 8))

  return (
    <svg viewBox={`0 0 ${width} ${height}`} width="100%" style={{ maxWidth: width, display: 'block' }}>
      {tickValues.map((v) => (
        <g key={v}>
          <line x1={MARGIN.left} x2={width - MARGIN.right} y1={y(v)} y2={y(v)} className="chart-gridline" />
          <text x={MARGIN.left - 8} y={y(v)} textAnchor="end" dominantBaseline="middle" className="chart-axis-label">
            {Math.round(v).toLocaleString()}
          </text>
        </g>
      ))}

      {candles.map((c, i) => {
        const up = c.close >= c.open
        const color = up ? UP_COLOR : DOWN_COLOR
        const bodyTop = y(Math.max(c.open, c.close))
        const bodyBottom = y(Math.min(c.open, c.close))
        return (
          <g key={c.date}>
            <line x1={x(i)} x2={x(i)} y1={y(c.high)} y2={y(c.low)} stroke={color} strokeWidth={1} />
            <rect
              x={x(i) - bodyWidth / 2}
              y={bodyTop}
              width={bodyWidth}
              height={Math.max(1, bodyBottom - bodyTop)}
              fill={color}
            />
          </g>
        )
      })}

      {candles.map((c, i) =>
        i % labelEvery === 0 || i === candles.length - 1 ? (
          <text key={c.date} x={x(i)} y={height - 4} textAnchor="middle" className="chart-axis-label">
            {c.date.slice(5)}
          </text>
        ) : null,
      )}
    </svg>
  )
}
