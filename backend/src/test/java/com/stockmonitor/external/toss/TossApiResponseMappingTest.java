package com.stockmonitor.external.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stockmonitor.domain.Market;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link TossHttpApiClient}'s response mapping to real captured responses.
 *
 * <p>These field names came from calling the live API, not from documentation — the docs
 * list the endpoints but not their schemas. That makes the mapping the most fragile part of
 * the client, so a schema change (or a bad guess) should fail here rather than silently
 * produce a zeroed-out dashboard.
 */
class TossApiResponseMappingTest {

	/** Verbatim body of a real {@code GET /api/v1/holdings} response. */
	private static final String HOLDINGS_RESPONSE = """
			{"result":{"totalPurchaseAmount":{"krw":"27140","usd":"3.159637"},\
			"marketValue":{"amount":{"krw":"26280","usd":"3.14647"},"amountAfterCost":{"krw":"26273","usd":"3.14647"}},\
			"profitLoss":{"amount":{"krw":"-860","usd":"-0.013167"},"amountAfterCost":{"krw":"-867","usd":"-0.013167"},\
			"rate":"-0.0315","rateAfterCost":"-0.0319"},\
			"dailyProfitLoss":{"amount":{"krw":"-85","usd":"0.0006"},"rate":"-0.0026"},\
			"items":[{"symbol":"360750","name":"TIGER 미국S&P500","marketCountry":"KR","currency":"KRW",\
			"quantity":"1","lastPrice":"26280","averagePurchasePrice":"27140",\
			"marketValue":{"purchaseAmount":"27140","amount":"26280","amountAfterCost":"26273"},\
			"profitLoss":{"amount":"-860","amountAfterCost":"-867","rate":"-0.0316","rateAfterCost":"-0.0319"},\
			"dailyProfitLoss":{"amount":"-85","rate":"-0.0031"},"cost":{"commission":"7","tax":null}},\
			{"symbol":"VOO","name":"VOO","marketCountry":"US","currency":"USD",\
			"quantity":"0.004448","lastPrice":"707.39","averagePurchasePrice":"710.350044",\
			"marketValue":{"purchaseAmount":"3.159637","amount":"3.14647","amountAfterCost":"3.14647"},\
			"profitLoss":{"amount":"-0.013167","amountAfterCost":"-0.013167","rate":"-0.0041","rateAfterCost":"-0.0041"},\
			"dailyProfitLoss":{"amount":"0.0006","rate":"0.0001"},"cost":{"commission":"0","tax":null}}]}}""";

	/** Verbatim body of a real {@code GET /api/v1/prices?symbols=005930} response. */
	private static final String PRICES_RESPONSE = """
			{"result":[{"symbol":"005930","timestamp":"2026-08-28T19:59:59.000+09:00",\
			"lastPrice":"256500","currency":"KRW"}]}""";

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	private TossHttpApiClient.HoldingsResult holdingsResult() throws Exception {
		return objectMapper.readValue(HOLDINGS_RESPONSE, TossHttpApiClient.HoldingsEnvelope.class).result();
	}

	@Test
	void mapsHoldingsFromARealResponse() throws Exception {
		List<Holding> holdings = TossHttpApiClient.toHoldings(holdingsResult());

		assertThat(holdings).hasSize(2);

		Holding kr = holdings.get(0);
		assertThat(kr.symbol()).isEqualTo("360750");
		assertThat(kr.name()).isEqualTo("TIGER 미국S&P500");
		assertThat(kr.market()).isEqualTo(Market.KR);
		assertThat(kr.quantity()).isEqualByComparingTo("1");
		assertThat(kr.avgPrice()).isEqualByComparingTo("27140");
		assertThat(kr.lastPrice()).isEqualByComparingTo("26280");

		Holding us = holdings.get(1);
		assertThat(us.symbol()).isEqualTo("VOO");
		// marketCountry "US" has to become Market.US, or US positions get priced as KRW.
		assertThat(us.market()).isEqualTo(Market.US);
		assertThat(us.quantity()).isEqualByComparingTo("0.004448");
		assertThat(us.avgPrice()).isEqualByComparingTo("710.350044");
		assertThat(us.lastPrice()).isEqualByComparingTo("707.39");
	}

	@Test
	void readsAccountTotalsInKrwAndConvertsTheDailyRateToPercent() throws Exception {
		AccountSummary summary = TossHttpApiClient.toAccountSummary(holdingsResult());

		assertThat(summary.totalValue()).isEqualByComparingTo("26280");
		assertThat(summary.dailyPnl()).isEqualByComparingTo("-85");
		// The API reports rates as fractions: "-0.0026" is -0.26%.
		assertThat(summary.dailyPnlRate()).isEqualByComparingTo("-0.26");
	}

	/**
	 * Three entries from a real {@code /api/v1/candles?symbol=005930&interval=1d} response,
	 * newest-first as the API returns them. The array's field name wasn't visible in the
	 * captured output, so {@code candleArray} takes the {@code result} object's sole array
	 * field — {@code items} here stands in for whatever it's actually called, and the test
	 * would still pass under a different name, which is the point.
	 */
	private static final String CANDLES_RESPONSE = """
			{"result":{"items":[\
			{"timestamp":"2026-06-08T00:00:00.000+09:00","openPrice":"304500","highPrice":"316000",\
			"lowPrice":"290500","closePrice":"303000","volume":"76298436","currency":"KRW"},\
			{"timestamp":"2026-06-05T00:00:00.000+09:00","openPrice":"343000","highPrice":"346000",\
			"lowPrice":"321000","closePrice":"329000","volume":"64419483","currency":"KRW"},\
			{"timestamp":"2026-06-04T00:00:00.000+09:00","openPrice":"359000","highPrice":"366500",\
			"lowPrice":"338500","closePrice":"342000","volume":"61113411","currency":"KRW"}],\
			"nextBefore":"2026-06-03T00:00:00.000+09:00"}}""";

	@Test
	void mapsCandlesOldestFirstAndTrimsToTheRequestedDays() throws Exception {
		List<Candle> candles = candlesFrom(CANDLES_RESPONSE, 60);

		assertThat(candles).hasSize(3);
		// The API returns newest-first; charts want oldest-first.
		assertThat(candles.stream().map(Candle::date))
				.containsExactly(LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 5), LocalDate.of(2026, 6, 8));

		Candle latest = candles.get(2);
		assertThat(latest.open()).isEqualByComparingTo("304500");
		assertThat(latest.high()).isEqualByComparingTo("316000");
		assertThat(latest.low()).isEqualByComparingTo("290500");
		assertThat(latest.close()).isEqualByComparingTo("303000");
		assertThat(latest.volume()).isEqualTo(76_298_436L);
	}

	@Test
	void keepsTheMostRecentCandlesWhenTheApiReturnsMoreThanRequested() throws Exception {
		List<Candle> candles = candlesFrom(CANDLES_RESPONSE, 2);

		assertThat(candles.stream().map(Candle::date))
				.containsExactly(LocalDate.of(2026, 6, 5), LocalDate.of(2026, 6, 8));
	}

	@Test
	void failsLoudlyRatherThanRenderingAnEmptyChartWhenThereIsNoCandleArray() {
		assertThatThrownBy(() -> candlesFrom("{\"result\":{\"nextBefore\":\"2026-06-03T00:00:00.000+09:00\"}}", 60))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("nextBefore");
	}

	/** Runs the response through the same mapping {@code getDailyCandles} uses. */
	private List<Candle> candlesFrom(String responseBody, int days) throws Exception {
		JsonNode result = objectMapper.readTree(responseBody).path("result");
		return TossHttpApiClient.toCandles(result, "005930", days, objectMapper);
	}

	@Test
	void mapsThePriceResponseWhoseLastPriceIsAJsonString() throws Exception {
		var result = objectMapper.readValue(PRICES_RESPONSE, TossHttpApiClient.PricesEnvelope.class).result();

		assertThat(result).hasSize(1);
		assertThat(result.get(0).symbol()).isEqualTo("005930");
		assertThat(result.get(0).lastPrice()).isEqualByComparingTo("256500");
		assertThat(result.get(0).timestamp()).isEqualTo("2026-08-28T10:59:59Z");
	}
}
