package com.stockmonitor.external.toss;

import com.stockmonitor.domain.Market;
import java.util.List;

/**
 * Abstraction over the Toss Securities Open API.
 *
 * <p><b>Status:</b> no real implementation exists yet — the actual endpoint paths, auth
 * flow (OAuth2 client-credentials per docs/PLANNING.md section 5) and request/response
 * shapes aren't documented anywhere this codebase could verify against. Everything in
 * the app is written against this interface only, so a {@code TossHttpApiClient} can be
 * dropped in later (see {@link MockTossApiClient}, the only bean implementing this today)
 * without touching callers.
 */
public interface TossApiClient {

	/** Latest quote for a single symbol. */
	Quote getQuote(String symbol, Market market);

	/** Account-level totals (평가금액 / 당일 손익). */
	AccountSummary getAccountSummary();

	/** Currently held positions. */
	List<Holding> getHoldings();

	/** Daily OHLCV bars, oldest first, for the candlestick chart. */
	List<Candle> getDailyCandles(String symbol, Market market, int days);
}
