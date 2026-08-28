package com.stockmonitor.external.toss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockmonitor.config.TossApiProperties;
import com.stockmonitor.domain.Market;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

/**
 * Real {@link TossApiClient} backed by HTTP calls to the Toss Securities Open API
 * (https://developers.tossinvest.com/docs).
 *
 * <p><b>Confirmed against the real docs:</b> the OAuth token flow ({@link TossOAuthTokenProvider}),
 * the base URL and every endpoint path below, the {@code X-Tossinvest-Account} header
 * requirement for account-scoped calls, and the error envelope
 * ({@code {"error": {"requestId","code","message","data"}}}, see {@link TossApiException}).
 *
 * <p><b>Still unverified:</b> the exact response body field names for
 * {@code GET /api/v1/prices}, {@code GET /api/v1/candles} and {@code GET /api/v1/holdings} —
 * the docs list these endpoints but the detail pages with full request/response schemas
 * weren't available when this was written. {@link QuoteDto}, {@link CandleDto} and
 * {@link HoldingDto} below are still best-guess field names; fix them once those pages
 * are available (paste them and the mapping is a small, isolated edit — everything
 * else in this file doesn't need to change).
 *
 * <p>This bean only activates when {@code toss.api.use-real-client=true} (see
 * {@link TossApiProperties}) — until then {@link MockTossApiClient} keeps serving the
 * app, so adding real credentials alone doesn't switch anything over silently.
 */
public class TossHttpApiClient implements TossApiClient {

	private static final Logger log = LoggerFactory.getLogger(TossHttpApiClient.class);

	private static final String PRICES_PATH = "/api/v1/prices";
	private static final String CANDLES_PATH = "/api/v1/candles";
	private static final String HOLDINGS_PATH = "/api/v1/holdings";
	private static final long DEFAULT_RETRY_SECONDS = 2;

	private final TossApiProperties properties;
	private final TossOAuthTokenProvider tokenProvider;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	public TossHttpApiClient(TossApiProperties properties, TossOAuthTokenProvider tokenProvider, ObjectMapper objectMapper) {
		this.properties = properties;
		this.tokenProvider = tokenProvider;
		this.objectMapper = objectMapper;

		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(5_000);
		requestFactory.setReadTimeout(10_000);
		// baseUrl() must be set here rather than concatenated into UriBuilder.path() below -
		// UriBuilder.path() treats its argument as a path segment to append, not a full
		// absolute URL, so "https://host" + "/path" gets merged into "https:/host/path"
		// (the double slash collapsed) instead of parsed as scheme+host.
		this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
	}

	@Override
	public Quote getQuote(String symbol, Market market) {
		// The docs' own getting-started example uses a plural "symbols" query param
		// (GET /api/v1/stocks?symbols=005930) for a single symbol; /api/v1/prices likely
		// follows the same convention. Response shape (single object vs. array keyed by
		// symbol) is unconfirmed - adjust QuoteDto/parsing once the Market Data > 현재가
		// 조회 page is available.
		QuoteDto dto = authorizedGet(PRICES_PATH, QuoteDto.class, uri -> uri.queryParam("symbols", symbol), false);
		return new Quote(
				symbol, market, dto.price(), dto.changeRate(), dto.volume(), dto.avgVolume(),
				dto.week52High(), dto.week52Low(), java.time.Instant.now());
	}

	@Override
	public List<Candle> getDailyCandles(String symbol, Market market, int days) {
		// Docs list "캔들 차트 조회 (1분봉 · 일봉)" without giving the interval/count
		// param names - guessing "interval"/"count" below; confirm once available.
		CandleDto[] dtos = authorizedGet(CANDLES_PATH, CandleDto[].class, uri -> uri
				.queryParam("symbols", symbol)
				.queryParam("interval", "1d")
				.queryParam("count", days), false);
		return List.of(dtos).stream()
				.map(d -> new Candle(d.date(), d.open(), d.high(), d.low(), d.close(), d.volume()))
				.toList();
	}

	@Override
	public List<Holding> getHoldings() {
		HoldingDto[] dtos = authorizedGet(HOLDINGS_PATH, HoldingDto[].class, uri -> uri, true);
		return List.of(dtos).stream()
				.map(d -> new Holding(d.symbol(), d.market(), d.name(), d.quantity(), d.avgPrice()))
				.toList();
	}

	@Override
	public AccountSummary getAccountSummary() {
		// The docs don't list a separate "계좌 요약" endpoint - only 계좌 목록 (accounts)
		// and 보유 주식 조회 (holdings, "종목별 상세 + 합산 평가" - it may already return an
		// aggregate, but until that schema is confirmed this sums holdings x live quotes
		// client-side, the same approach MockTossApiClient uses.
		BigDecimal totalValue = BigDecimal.ZERO;
		BigDecimal totalCost = BigDecimal.ZERO;
		for (Holding h : getHoldings()) {
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

	private String requireAccountSeq() {
		String seq = properties.accountSeq();
		if (seq == null || seq.isBlank()) {
			throw new IllegalStateException(
					"toss.api.account-seq가 설정되어 있지 않습니다. GET " + properties.baseUrl() + "/api/v1/accounts 로 계좌 목록을 조회해 accountSeq를 확인한 뒤 설정하세요.");
		}
		return seq;
	}

	/** One retry on 429, honoring Retry-After, per the docs' rate-limit guidance. */
	private <T> T authorizedGet(String path, Class<T> responseType, Function<UriBuilder, UriBuilder> queryParams, boolean requiresAccount) {
		Supplier<T> call = () -> restClient.get()
				.uri(uriBuilder -> queryParams.apply(uriBuilder.path(path)).build())
				.headers(headers -> {
					headers.setBearerAuth(tokenProvider.getAccessToken());
					if (requiresAccount) {
						headers.set("X-Tossinvest-Account", requireAccountSeq());
					}
				})
				.retrieve()
				.onStatus(HttpStatusCode::isError, this::handleErrorResponse)
				.body(responseType);

		try {
			return call.get();
		} catch (RetryAfterSignal retry) {
			log.warn("Rate limited on {}, retrying once after {}s", path, retry.seconds);
			try {
				Thread.sleep(retry.seconds * 1000L);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return call.get();
		}
	}

	private void handleErrorResponse(HttpRequest request, ClientHttpResponse response) throws IOException {
		int status = response.getStatusCode().value();
		if (status == 429) {
			long seconds = parseRetryAfter(response);
			throw new RetryAfterSignal(seconds);
		}

		String body = TossErrorBodyReader.readAsText(response);
		try {
			TossErrorEnvelope envelope = objectMapper.readValue(body, TossErrorEnvelope.class);
			throw new TossApiException(status, envelope.error().code(), envelope.error().message(), envelope.error().requestId());
		} catch (IOException e) {
			throw new TossApiException(status, "unknown",
					"에러 응답을 파싱할 수 없습니다 (HTTP " + status + "). 응답 본문: " + snippet(body), null);
		}
	}

	private static String snippet(String body) {
		String trimmed = body.strip();
		if (trimmed.isEmpty()) {
			return "(empty)";
		}
		return trimmed.length() > 300 ? trimmed.substring(0, 300) + "..." : trimmed;
	}

	private long parseRetryAfter(ClientHttpResponse response) throws IOException {
		String header = response.getHeaders().getFirst("Retry-After");
		if (header == null) {
			return DEFAULT_RETRY_SECONDS;
		}
		try {
			return Long.parseLong(header.trim());
		} catch (NumberFormatException e) {
			return DEFAULT_RETRY_SECONDS;
		}
	}

	/** Internal signal caught by {@link #authorizedGet} to trigger exactly one retry. */
	private static final class RetryAfterSignal extends RuntimeException {
		private final long seconds;

		private RetryAfterSignal(long seconds) {
			super("rate limited, retry after " + seconds + "s", null, false, false);
			this.seconds = seconds;
		}
	}

	// --- Response DTOs below: field names are best-guess, see class Javadoc. ---

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record QuoteDto(
			BigDecimal price,
			@JsonProperty("changeRate") BigDecimal changeRate,
			long volume,
			@JsonProperty("avgVolume") long avgVolume,
			@JsonProperty("week52High") BigDecimal week52High,
			@JsonProperty("week52Low") BigDecimal week52Low) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record HoldingDto(
			String symbol, Market market, String name, BigDecimal quantity, @JsonProperty("avgPrice") BigDecimal avgPrice) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record CandleDto(LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {
	}
}
