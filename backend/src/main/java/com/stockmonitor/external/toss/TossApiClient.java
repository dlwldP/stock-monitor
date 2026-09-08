package com.stockmonitor.external.toss;

import com.stockmonitor.domain.Market;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstraction over the Toss Securities Open API.
 *
 * <p>Two implementations: {@link MockTossApiClient} (the default, random-walk prices, so the
 * app runs with no credentials at all) and {@link TossHttpApiClient} (the real API, active
 * only when {@code toss.api.use-real-client=true}). Everything else in the app is written
 * against this interface, so neither one leaks into callers.
 */
public interface TossApiClient {

	/** Latest quote for a single symbol. */
	Quote getQuote(String symbol, Market market);

	/**
	 * Latest quotes for several symbols at once.
	 *
	 * <p>Callers that need more than one quote should prefer this over a loop of
	 * {@link #getQuote}: an implementation backed by a rate-limited HTTP API can serve the
	 * whole set in one request, which matters because both the alert scheduler and the
	 * watchlist screen routinely ask for many symbols at once (and often the same symbol
	 * more than once).
	 *
	 * <p>The returned map holds an entry only for symbols that could actually be fetched —
	 * one bad symbol doesn't fail the batch — so callers must handle absence. Duplicate refs
	 * collapse to one lookup.
	 *
	 * <p>The default implementation just loops, which is the right behaviour for a local
	 * implementation with no per-request cost.
	 */
	default Map<SymbolRef, Quote> getQuotes(Collection<SymbolRef> refs) {
		Map<SymbolRef, Quote> quotes = new LinkedHashMap<>();
		for (SymbolRef ref : refs) {
			if (!quotes.containsKey(ref)) {
				quotes.put(ref, getQuote(ref.symbol(), ref.market()));
			}
		}
		return quotes;
	}

	/** Account-level totals (평가금액 / 당일 손익). */
	AccountSummary getAccountSummary();

	/** Currently held positions. */
	List<Holding> getHoldings();

	/**
	 * Orders placed but not fully filled yet (미체결 주문).
	 *
	 * <p>Holdings only show settled positions, so without this there's no way to see that an
	 * order is in flight — the gap between placing one and it appearing in the account.
	 */
	List<PendingOrder> getPendingOrders();

	/** Daily OHLCV bars, oldest first, for the candlestick chart. */
	List<Candle> getDailyCandles(String symbol, Market market, int days);
}
