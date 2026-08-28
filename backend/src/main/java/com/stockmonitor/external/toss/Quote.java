package com.stockmonitor.external.toss;

import com.stockmonitor.domain.Market;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * A point-in-time price snapshot, as returned by {@link TossApiClient#getQuote}.
 *
 * <p>Only {@code symbol}, {@code market}, {@code price} and {@code updatedAt} are always
 * populated. The rest depend on what the backing implementation can supply:
 * {@link MockTossApiClient} fills in everything, while the real
 * {@link TossHttpApiClient} leaves them {@code null}/{@code 0} because
 * {@code GET /api/v1/prices} only returns the last traded price. Consumers must handle
 * their absence — {@link com.stockmonitor.domain.AlertRule#isSatisfiedBy} treats a
 * condition with missing input as simply not satisfied.
 *
 * @param changeRate percent change vs. previous close, e.g. {@code 1.23} means +1.23%; may be null
 * @param volume     traded volume; {@code 0} when unknown
 * @param avgVolume  baseline (e.g. 20-day average) volume, used by the VOLUME_SPIKE alert condition; {@code 0} when unknown
 * @param week52High 52-week high price, used by the WEEK52_HIGH_NEAR alert condition; may be null
 * @param week52Low  52-week low price, used by the WEEK52_LOW_NEAR alert condition; may be null
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
