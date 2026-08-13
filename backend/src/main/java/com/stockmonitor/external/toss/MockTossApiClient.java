package com.stockmonitor.external.toss;

import com.stockmonitor.domain.Market;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * Stand-in for the real Toss Securities API (see {@link TossApiClient}). Prices do a
 * small random walk on every call so the dashboard looks alive and alert rules have a
 * realistic chance to fire during manual testing. Not backed by any real market data.
 */
@Component
public class MockTossApiClient implements TossApiClient {

	/** symbol -> (previous-close, current price). Doubles as the "seed" price on first access. */
	private final Map<String, PriceState> prices = new ConcurrentHashMap<>();

	private static final Map<String, BigDecimal> SEED_PRICES = Map.of(
			"005930", new BigDecimal("71000"), // 삼성전자
			"000660", new BigDecimal("185000"), // SK하이닉스
			"AAPL", new BigDecimal("230.00"),
			"TSLA", new BigDecimal("250.00"),
			"NVDA", new BigDecimal("130.00"));

	private static final List<Holding> MOCK_HOLDINGS = List.of(
			new Holding("005930", Market.KR, "삼성전자", new BigDecimal("10"), new BigDecimal("65000")),
			new Holding("AAPL", Market.US, "Apple Inc.", new BigDecimal("5"), new BigDecimal("210.00")));

	@Override
	public Quote getQuote(String symbol, Market market) {
		PriceState state = prices.computeIfAbsent(symbol, s -> {
			BigDecimal seed = SEED_PRICES.getOrDefault(s, seedFromHash(s, market));
			return new PriceState(seed, seed);
		});

		BigDecimal current;
		synchronized (state) {
			// +/-1.5% random step per call.
			double stepPct = (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.03;
			BigDecimal factor = BigDecimal.valueOf(1 + stepPct);
			state.current = state.current.multiply(factor).setScale(scaleFor(market), RoundingMode.HALF_UP);
			current = state.current;
		}

		BigDecimal changeRate = current.subtract(state.previousClose)
				.divide(state.previousClose, 6, RoundingMode.HALF_UP)
				.multiply(BigDecimal.valueOf(100))
				.setScale(2, RoundingMode.HALF_UP);

		long volume = ThreadLocalRandom.current().nextLong(10_000, 5_000_000);
		return new Quote(symbol, market, current, changeRate, volume, Instant.now());
	}

	@Override
	public AccountSummary getAccountSummary() {
		BigDecimal totalValue = BigDecimal.ZERO;
		BigDecimal totalCost = BigDecimal.ZERO;
		for (Holding h : MOCK_HOLDINGS) {
			Quote quote = getQuote(h.symbol(), h.market());
			totalValue = totalValue.add(quote.price().multiply(h.quantity()));
			totalCost = totalCost.add(h.avgPrice().multiply(h.quantity()));
		}
		BigDecimal dailyPnl = totalValue.subtract(totalCost);
		BigDecimal dailyPnlRate = totalCost.signum() == 0
				? BigDecimal.ZERO
				: dailyPnl.divide(totalCost, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
		return new AccountSummary(totalValue.setScale(2, RoundingMode.HALF_UP), dailyPnl.setScale(2, RoundingMode.HALF_UP), dailyPnlRate);
	}

	@Override
	public List<Holding> getHoldings() {
		return MOCK_HOLDINGS;
	}

	private static BigDecimal seedFromHash(String symbol, Market market) {
		int h = Math.abs((symbol + market).hashCode());
		return market == Market.KR
				? BigDecimal.valueOf(10_000 + h % 90_000)
				: BigDecimal.valueOf(20 + h % 480).setScale(2, RoundingMode.HALF_UP);
	}

	private static int scaleFor(Market market) {
		return market == Market.KR ? 0 : 2;
	}

	private static final class PriceState {
		private final BigDecimal previousClose;
		private BigDecimal current;

		private PriceState(BigDecimal previousClose, BigDecimal current) {
			this.previousClose = previousClose;
			this.current = current;
		}
	}
}
