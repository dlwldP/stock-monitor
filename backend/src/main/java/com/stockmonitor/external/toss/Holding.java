package com.stockmonitor.external.toss;

import com.stockmonitor.domain.Market;
import java.math.BigDecimal;

/**
 * A held position, as returned by {@link TossApiClient#getHoldings}. Current price / P&amp;L
 * are derived by the caller from a {@link Quote}, not carried here, since this is meant to
 * mirror the raw account-holdings response shape.
 */
public record Holding(String symbol, Market market, String name, BigDecimal quantity, BigDecimal avgPrice) {
}
