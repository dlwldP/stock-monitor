package com.stockmonitor.external.toss;

import com.stockmonitor.domain.Market;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A point-in-time price snapshot, as returned by {@link TossApiClient#getQuote}.
 *
 * @param changeRate percent change vs. previous close, e.g. {@code 1.23} means +1.23%
 * @param avgVolume  baseline (e.g. 20-day average) volume, used by the VOLUME_SPIKE alert condition
 * @param week52High 52-week high price, used by the WEEK52_HIGH_NEAR alert condition
 * @param week52Low  52-week low price, used by the WEEK52_LOW_NEAR alert condition
 */
public record Quote(
		String symbol,
		Market market,
		BigDecimal price,
		BigDecimal changeRate,
		long volume,
		long avgVolume,
		BigDecimal week52High,
		BigDecimal week52Low,
		Instant updatedAt) {
}
