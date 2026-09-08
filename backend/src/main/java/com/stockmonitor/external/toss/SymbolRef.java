package com.stockmonitor.external.toss;

import com.stockmonitor.domain.Market;

/**
 * Identifies one tradeable symbol, for the batched {@link TossApiClient#getQuotes} lookup.
 *
 * <p>A symbol alone isn't a key: the same ticker can exist on more than one market, and the
 * market decides how the price is scaled and which calendar its trading day follows.
 */
public record SymbolRef(String symbol, Market market) {
}
