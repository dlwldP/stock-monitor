package com.stockmonitor.external.toss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockmonitor.config.TossApiProperties;
import com.stockmonitor.domain.Market;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * Real {@link TossApiClient} backed by HTTP calls to the Toss Securities Open API.
 *
 * <p><b>The endpoint paths and response field names below are unverified placeholders,
 * not confirmed against real API documentation</b> (none was available when this was
 * written — see docs/PLANNING.md section 5 and the project README). They follow
 * reasonable REST conventions and the category names the planning doc does give
 * (Market Data / Account and Asset), but every {@code URI} constant and DTO field in
 * this file should be checked against the actual docs and fixed before relying on it.
 * The OAuth token flow in {@link TossOAuthTokenProvider} is the one part written
 * directly from the documented spec and is more likely correct as-is.
 *
 * <p>This bean only activates when {@code toss.api.use-real-client=true} (see
 * {@link TossApiProperties}) — until then {@link MockTossApiClient} keeps serving the
 * app, so adding real credentials alone doesn't switch anything over silently.
 */
public class TossHttpApiClient implements TossApiClient {

	// Adjust these once the real paths are known.
	private static final String QUOTE_PATH = "/v1/market-data/quote";
	private static final String CANDLES_PATH = "/v1/market-data/candles";
	private static final String ACCOUNT_SUMMARY_PATH = "/v1/accounts/summary";
	private static final String HOLDINGS_PATH = "/v1/accounts/holdings";

	private final TossApiProperties properties;
	private final TossOAuthTokenProvider tokenProvider;
	private final RestClient restClient;

	public TossHttpApiClient(TossApiProperties properties, TossOAuthTokenProvider tokenProvider) {
		this.properties = properties;
		this.tokenProvider = tokenProvider;

		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(5_000);
		requestFactory.setReadTimeout(10_000);
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
	}

	@Override
	public Quote getQuote(String symbol, Market market) {
		QuoteDto dto = authorizedGet(QUOTE_PATH, QuoteDto.class, uri -> uri
				.queryParam("symbol", symbol)
				.queryParam("market", market));
		return new Quote(
				symbol, market, dto.price(), dto.changeRate(), dto.volume(), dto.avgVolume(),
				dto.week52High(), dto.week52Low(), Instant.now());
	}

	@Override
	public AccountSummary getAccountSummary() {
		AccountSummaryDto dto = authorizedGet(ACCOUNT_SUMMARY_PATH, AccountSummaryDto.class, uri -> uri);
		return new AccountSummary(dto.totalValue(), dto.dailyPnl(), dto.dailyPnlRate());
	}

	@Override
	public List<Holding> getHoldings() {
		HoldingDto[] dtos = authorizedGet(HOLDINGS_PATH, HoldingDto[].class, uri -> uri);
		return List.of(dtos).stream()
				.map(d -> new Holding(d.symbol(), d.market(), d.name(), d.quantity(), d.avgPrice()))
				.toList();
	}

	@Override
	public List<Candle> getDailyCandles(String symbol, Market market, int days) {
		CandleDto[] dtos = authorizedGet(CANDLES_PATH, CandleDto[].class, uri -> uri
				.queryParam("symbol", symbol)
				.queryParam("market", market)
				.queryParam("days", days));
		return List.of(dtos).stream()
				.map(d -> new Candle(d.date(), d.open(), d.high(), d.low(), d.close(), d.volume()))
				.toList();
	}

	private <T> T authorizedGet(String path, Class<T> responseType, Function<UriBuilder, UriBuilder> queryParams) {
		return restClient.get()
				.uri(uriBuilder -> queryParams.apply(uriBuilder.path(properties.baseUrl() + path)).build())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenProvider.getAccessToken())
				.retrieve()
				.body(responseType);
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record QuoteDto(
			BigDecimal price,
			@JsonProperty("change_rate") BigDecimal changeRate,
			long volume,
			@JsonProperty("avg_volume") long avgVolume,
			@JsonProperty("week52_high") BigDecimal week52High,
			@JsonProperty("week52_low") BigDecimal week52Low) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record AccountSummaryDto(
			@JsonProperty("total_value") BigDecimal totalValue,
			@JsonProperty("daily_pnl") BigDecimal dailyPnl,
			@JsonProperty("daily_pnl_rate") BigDecimal dailyPnlRate) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record HoldingDto(
			String symbol, Market market, String name, BigDecimal quantity, @JsonProperty("avg_price") BigDecimal avgPrice) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record CandleDto(LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {
	}
}
