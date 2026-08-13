package com.stockmonitor.external.toss;

import com.stockmonitor.domain.Market;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A point-in-time price snapshot, as returned by {@link TossApiClient#getQuote}.
 *
 * @param changeRate percent change vs. previous close, e.g. {@code 1.23} means +1.23%
 */
public record Quote(String symbol, Market market, BigDecimal price, BigDecimal changeRate, long volume, Instant updatedAt) {
}
