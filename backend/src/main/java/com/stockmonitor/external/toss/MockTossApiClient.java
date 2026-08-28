package com.stockmonitor.external.toss;

import com.stockmonitor.domain.Market;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stand-in for the real Toss Securities API (see {@link TossApiClient}). Prices do a
 * small random walk on every call so the dashboard looks alive and alert rules have a
 * realistic chance to fire during manual testing. Not backed by any real market data.
 *
 * <p>52-week high/low and average volume are derived once from the seed price and held
 * fixed per symbol — a real implementation would pull these from actual history.
 *
 * <p>Registered as a bean by {@link com.stockmonitor.config.TossApiClientConfig}, not via
 * {@code @Component} directly, so it can be swapped for {@link TossHttpApiClient} based on
 * the {@code toss.api.use-real-client} setting.
 */
public class MockTossApiClient implements TossApiClient {

	/** symbol -> price/volume state. Doubles as the "seed" price on first access. */
	private final Map<String, PriceState> prices = new ConcurrentHashMap<>();

	private static final Map<String, BigDecimal> SEED_PRICES = Map.of(
			"005930", new BigDecimal("71000"), // 삼성전자
			"000660", new BigDecimal("185000"), // SK하이닉스
			"AAPL", new BigDecimal("230.00"),
			"TSLA", new BigDecimal("250.00"),
			"NVDA", new BigDecimal("130.00"));

	private static final List<Holding> MOCK_HOLDINGS = List.of(
			new Holding("005930", Market.KR, "삼성전자", new BigDecimal("10"), new BigDecimal("65000"), null),
			new Holding("AAPL", Market.US, "Apple Inc.", new BigDecimal("5"), new BigDecimal("210.00"), null));

	@Override
	public Quote getQuote(String symbol, Market market) {
		PriceState state = stateFor(symbol, market);

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

		// Random around the baseline so VOLUME_SPIKE occasionally has something to catch,
		// including an occasional deliberate spike (3x-6x) to make the condition testable.
		boolean spike = ThreadLocalRandom.current().nextInt(10) == 0;
		long volume = spike
				? state.avgVolume * ThreadLocalRandom.current().nextInt(3, 6)
				: ThreadLocalRandom.current().nextLong(state.avgVolume / 4, state.avgVolume + state.avgVolume / 2);

		return new Quote(symbol, market, current, changeRate, volume, state.avgVolume, state.week52High, state.week52Low, Instant.now());
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

	@Override
	public List<Candle> getDailyCandles(String symbol, Market market, int days) {
		PriceState state = stateFor(symbol, market);
		int scale = scaleFor(market);

		// Deterministic per symbol so repeated requests render the same chart, independent
		// of the live random-walk state used by getQuote.
		Random rnd = new Random((symbol + market).hashCode());
		BigDecimal price = state.previousClose;
		LocalDate date = LocalDate.now().minusDays(days);

		List<Candle> candles = new ArrayList<>(days);
		for (int i = 0; i < days; i++) {
			date = date.plusDays(1);
			BigDecimal open = price;
			double stepPct = (rnd.nextDouble() - 0.5) * 0.04; // +/-2% per day
			BigDecimal close = open.multiply(BigDecimal.valueOf(1 + stepPct)).setScale(scale, RoundingMode.HALF_UP);
			BigDecimal high = open.max(close).multiply(BigDecimal.valueOf(1 + rnd.nextDouble() * 0.01)).setScale(scale, RoundingMode.HALF_UP);
			BigDecimal low = open.min(close).multiply(BigDecimal.valueOf(1 - rnd.nextDouble() * 0.01)).setScale(scale, RoundingMode.HALF_UP);
			long volume = state.avgVolume / 2 + (long) (rnd.nextDouble() * state.avgVolume);
			candles.add(new Candle(date, open, high, low, close, volume));
			price = close;
		}
		return candles;
	}

	private PriceState stateFor(String symbol, Market market) {
		return prices.computeIfAbsent(symbol, s -> {
			BigDecimal seed = SEED_PRICES.getOrDefault(s, seedFromHash(s, market));
			int scale = scaleFor(market);
			return new PriceState(
					seed,
					seed,
					seed.multiply(new BigDecimal("1.25")).setScale(scale, RoundingMode.HALF_UP),
					seed.multiply(new BigDecimal("0.75")).setScale(scale, RoundingMode.HALF_UP),
					// stable per-symbol baseline volume, independent of the random walk below
					500_000L + Math.abs((s + market).hashCode()) % 2_000_000L);
		});
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
		private final BigDecimal week52High;
		private final BigDecimal week52Low;
		private final long avgVolume;

		private PriceState(BigDecimal previousClose, BigDecimal current, BigDecimal week52High, BigDecimal week52Low, long avgVolume) {
			this.previousClose = previousClose;
			this.current = current;
			this.week52High = week52High;
			this.week52Low = week52Low;
			this.avgVolume = avgVolume;
		}
	}
}
