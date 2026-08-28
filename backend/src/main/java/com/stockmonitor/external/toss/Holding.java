package com.stockmonitor.external.toss;

import com.stockmonitor.domain.Market;
import java.math.BigDecimal;

/**
 * A held position, as returned by {@link TossApiClient#getHoldings}.
 *
 * @param lastPrice current price in the position's own currency, when the source supplies
 *                  it alongside the holding (the real Toss holdings response does). May be
 *                  {@code null}, in which case callers fall back to {@link TossApiClient#getQuote}.
 */
public record Holding(
		String symbol, Market market, String name, BigDecimal quantity, BigDecimal avgPrice, BigDecimal lastPrice) {
}
