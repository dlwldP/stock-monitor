package com.stockmonitor.external.toss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stockmonitor.domain.Market;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link TossHttpApiClient}'s response mapping to the real API's response shapes.
 *
 * <p>These field names came from calling the live API, not from documentation — the docs
 * list the endpoints but not their schemas. That makes the mapping the most fragile part of
 * the client, so a schema change (or a bad guess) should fail here rather than silently
 * produce a zeroed-out dashboard.
 *
 * <p>The fixtures below reproduce the real responses' <b>structure</b> exactly — field names,
 * nesting, string-encoded numbers, fractional rates, ordering — with made-up symbols and
 * amounts. That's what these tests are actually pinning, and it keeps a real account's
 * positions and balances out of a public repository.
 */
class TossApiResponseMappingTest {

	/** Shape of a real {@code GET /api/v1/holdings} response; values are invented. */
	private static final String HOLDINGS_RESPONSE = """
			{"result":{"totalPurchaseAmount":{"krw":"100000","usd":"11.640000"},\
			"marketValue":{"amount":{"krw":"96000","usd":"11.174400"},"amountAfterCost":{"krw":"95950","usd":"11.174400"}},\
			"profitLoss":{"amount":{"krw":"-4000","usd":"-0.465600"},"amountAfterCost":{"krw":"-4050","usd":"-0.465600"},\
			"rate":"-0.0400","rateAfterCost":"-0.0405"},\
			"dailyProfitLoss":{"amount":{"krw":"-250","usd":"-0.029100"},"rate":"-0.0026"},\
			"items":[{"symbol":"000000","name":"샘플 KR 종목","marketCountry":"KR","currency":"KRW",\
			"quantity":"2","lastPrice":"48000","averagePurchasePrice":"50000",\
			"marketValue":{"purchaseAmount":"100000","amount":"96000","amountAfterCost":"95950"},\
			"profitLoss":{"amount":"-4000","amountAfterCost":"-4050","rate":"-0.0400","rateAfterCost":"-0.0405"},\
			"dailyProfitLoss":{"amount":"-250","rate":"-0.0026"},"cost":{"commission":"50","tax":null}},\
			{"symbol":"SAMPLE","name":"Sample US Stock","marketCountry":"US","currency":"USD",\
			"quantity":"0.010000","lastPrice":"123.45","averagePurchasePrice":"130.500000",\
			"marketValue":{"purchaseAmount":"1.305000","amount":"1.234500","amountAfterCost":"1.234500"},\
			"profitLoss":{"amount":"-0.070500","amountAfterCost":"-0.070500","rate":"-0.0540","rateAfterCost":"-0.0540"},\
			"dailyProfitLoss":{"amount":"-0.002000","rate":"-0.0016"},"cost":{"commission":"0","tax":null}}]}}""";

	/** Shape of a real {@code GET /api/v1/prices?symbols=...} response; values are invented. */
	private static final String PRICES_RESPONSE = """
			{"result":[{"symbol":"000000","timestamp":"2026-08-28T19:59:59.000+09:00",\
			"lastPrice":"48000","currency":"KRW"}]}""";

	private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

	private TossHttpApiClient.HoldingsResult holdingsResult() throws Exception {
		return objectMapper.readValue(HOLDINGS_RESPONSE, TossHttpApiClient.HoldingsEnvelope.class).result();
	}

	@Test
	void mapsHoldingsFromTheRealResponseShape() throws Exception {
		List<Holding> holdings = TossHttpApiClient.toHoldings(holdingsResult());

		assertThat(holdings).hasSize(2);

		Holding kr = holdings.get(0);
		assertThat(kr.symbol()).isEqualTo("000000");
		assertThat(kr.name()).isEqualTo("샘플 KR 종목");
		assertThat(kr.market()).isEqualTo(Market.KR);
		assertThat(kr.quantity()).isEqualByComparingTo("2");
		assertThat(kr.avgPrice()).isEqualByComparingTo("50000");
		assertThat(kr.lastPrice()).isEqualByComparingTo("48000");

		Holding us = holdings.get(1);
		assertThat(us.symbol()).isEqualTo("SAMPLE");
		// marketCountry "US" has to become Market.US, or US positions get priced as KRW.
		assertThat(us.market()).isEqualTo(Market.US);
		assertThat(us.quantity()).isEqualByComparingTo("0.010000");
		assertThat(us.avgPrice()).isEqualByComparingTo("130.500000");
		assertThat(us.lastPrice()).isEqualByComparingTo("123.45");
	}

	@Test
	void readsAccountTotalsInKrwAndConvertsTheDailyRateToPercent() throws Exception {
		AccountSummary summary = TossHttpApiClient.toAccountSummary(holdingsResult());

		assertThat(summary.totalValue()).isEqualByComparingTo("96000");
		assertThat(summary.dailyPnl()).isEqualByComparingTo("-250");
		// The API reports rates as fractions: "-0.0026" is -0.26%.
		assertThat(summary.dailyPnlRate()).isEqualByComparingTo("-0.26");
	}

	/**
	 * Three entries in the shape of a real {@code /api/v1/candles?symbol=...&interval=1d}
	 * response, newest-first as the API returns them; the prices are invented.
	 *
	 * <p>The array's field name wasn't visible in the captured output, so {@code candleArray}
	 * takes the {@code result} object's sole array field — {@code items} here stands in for
	 * whatever it's actually called, and the test would still pass under a different name,
	 * which is the point.
	 */
	private static final String CANDLES_RESPONSE = """
			{"result":{"items":[\
			{"timestamp":"2026-06-08T00:00:00.000+09:00","openPrice":"47000","highPrice":"48500",\
			"lowPrice":"46500","closePrice":"48000","volume":"1200000","currency":"KRW"},\
			{"timestamp":"2026-06-05T00:00:00.000+09:00","openPrice":"46000","highPrice":"47200",\
			"lowPrice":"45800","closePrice":"47000","volume":"980000","currency":"KRW"},\
			{"timestamp":"2026-06-04T00:00:00.000+09:00","openPrice":"45500","highPrice":"46300",\
			"lowPrice":"45000","closePrice":"46000","volume":"1050000","currency":"KRW"}],\
			"nextBefore":"2026-06-03T00:00:00.000+09:00"}}""";

	@Test
	void mapsCandlesOldestFirstAndTrimsToTheRequestedDays() throws Exception {
		List<Candle> candles = candlesFrom(CANDLES_RESPONSE, 60);

		assertThat(candles).hasSize(3);
		// The API returns newest-first; charts want oldest-first.
		assertThat(candles.stream().map(Candle::date))
				.containsExactly(LocalDate.of(2026, 6, 4), LocalDate.of(2026, 6, 5), LocalDate.of(2026, 6, 8));

		Candle latest = candles.get(2);
		assertThat(latest.open()).isEqualByComparingTo("47000");
		assertThat(latest.high()).isEqualByComparingTo("48500");
		assertThat(latest.low()).isEqualByComparingTo("46500");
		assertThat(latest.close()).isEqualByComparingTo("48000");
		assertThat(latest.volume()).isEqualTo(1_200_000L);
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
		return TossHttpApiClient.toCandles(result, "000000", days, objectMapper);
	}

	@Test
	void mapsThePriceResponseWhoseLastPriceIsAJsonString() throws Exception {
		var result = objectMapper.readValue(PRICES_RESPONSE, TossHttpApiClient.PricesEnvelope.class).result();

		assertThat(result).hasSize(1);
		assertThat(result.get(0).symbol()).isEqualTo("000000");
		assertThat(result.get(0).lastPrice()).isEqualByComparingTo("48000");
	}

	@Test
	void parsesThePriceTimestampWithoutNormalizingItsOffsetAway() throws Exception {
		// Same class of bug as the candle date shift: binding this as Instant/OffsetDateTime
		// directly would let Jackson's default ADJUST_DATES_TO_CONTEXT_TIME_ZONE convert it to
		// UTC before this code ever sees the market's own trading day.
		var result = objectMapper.readValue(PRICES_RESPONSE, TossHttpApiClient.PricesEnvelope.class).result();

		OffsetDateTime timestamp = result.get(0).parsedTimestamp();
		assertThat(timestamp.toLocalDate()).isEqualTo(LocalDate.of(2026, 8, 28));
		assertThat(timestamp.getOffset()).isEqualTo(java.time.ZoneOffset.ofHours(9));
	}

	@Test
	void enrichmentComputesChangeRateFromThePreviousTradingDaysClose() {
		TossHttpApiClient.QuoteEnrichment enrichment =
				TossHttpApiClient.computeEnrichment(threeSampleCandles(), new BigDecimal("48960"), LocalDate.of(2026, 6, 9));

		// 48960 vs the 06-08 close (48000): +2%.
		assertThat(enrichment.changeRate()).isEqualByComparingTo("2.00");
	}

	@Test
	void enrichmentLeavesChangeRateNullWithNoPriorTradingDay() {
		TossHttpApiClient.QuoteEnrichment enrichment =
				TossHttpApiClient.computeEnrichment(threeSampleCandles(), new BigDecimal("45000"), LocalDate.of(2026, 6, 4));

		assertThat(enrichment.changeRate()).isNull();
	}

	@Test
	void enrichmentWidensThe52WeekRangeWithTheLivePriceWithoutLosingHistoricalExtremes() {
		// Live price makes a new high; historical low (45000 on 06-04) should still win the low.
		TossHttpApiClient.QuoteEnrichment enrichment =
				TossHttpApiClient.computeEnrichment(threeSampleCandles(), new BigDecimal("50000"), LocalDate.of(2026, 6, 9));

		assertThat(enrichment.week52High()).isEqualByComparingTo("50000");
		assertThat(enrichment.week52Low()).isEqualByComparingTo("45000");
	}

	@Test
	void enrichmentReadsVolumeFromTheMostRecentCandleAndAveragesThePriorDays() {
		TossHttpApiClient.QuoteEnrichment enrichment =
				TossHttpApiClient.computeEnrichment(threeSampleCandles(), new BigDecimal("48000"), LocalDate.of(2026, 6, 9));

		assertThat(enrichment.volume()).isEqualTo(1_200_000L);
		// average of the two prior days (980000, 1050000), not including today's own volume.
		assertThat(enrichment.avgVolume()).isEqualTo(1_015_000L);
	}

	@Test
	void enrichmentIsEmptyWithNoCandleHistory() {
		TossHttpApiClient.QuoteEnrichment enrichment =
				TossHttpApiClient.computeEnrichment(List.of(), new BigDecimal("48000"), LocalDate.of(2026, 6, 9));

		assertThat(enrichment).isEqualTo(TossHttpApiClient.QuoteEnrichment.EMPTY);
	}

	/** The three candles from {@link #CANDLES_RESPONSE}, already mapped to {@link Candle}. */
	private List<Candle> threeSampleCandles() {
		try {
			return candlesFrom(CANDLES_RESPONSE, 60);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
