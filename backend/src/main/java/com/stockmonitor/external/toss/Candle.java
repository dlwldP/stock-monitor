package com.stockmonitor.external.toss;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One daily OHLCV bar, as returned by {@link TossApiClient#getDailyCandles}. */
public record Candle(LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {
}
