package com.stockmonitor.external.toss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockmonitor.config.TossApiProperties;
import com.stockmonitor.domain.Market;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
 * <p>Also confirmed by calling the real API: every response wraps its payload in a
 * {@code {"result": ...}} envelope (see {@link ResultEnvelope}), and the
 * {@code X-Tossinvest-Account} header takes the {@code accountSeq} value from
 * {@code GET /api/v1/accounts} — not the human-readable {@code accountNo}.
 *
 * <p>{@code /api/v1/prices} and {@code /api/v1/holdings} were mapped by calling the live API
 * and reading the response, since the docs list the endpoints without their schemas;
 * {@code TossApiResponseMappingTest} pins both mappings to a captured response so a schema
 * change fails loudly instead of quietly zeroing out the dashboard.
 *
 * <p><b>Still unverified:</b> {@code GET /api/v1/candles}, which rejects the parameter names
 * guessed in {@link #getDailyCandles} with {@code invalid-request}. The {@code getRaw*}
 * methods here (exposed as {@code GET /api/toss/raw/*}, plus a free-form
 * {@code GET /api/toss/raw?path=...}) dump untouched JSON for probing it; once the right
 * parameters are known, only {@link #getDailyCandles} and {@link CandleDto} need to change.
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
	private static final String ACCOUNTS_PATH = "/api/v1/accounts";
	/** Unconfirmed — see {@link #getDailyCandles}. */
	private static final String DAILY_INTERVAL = "1d";
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

	/**
	 * {@inheritDoc}
	 *
	 * <p><b>Note:</b> {@code GET /api/v1/prices} returns only the last traded price (see
	 * {@link QuoteDto}), so the returned {@link Quote} has {@code null}/{@code 0} for
	 * change rate, volume and the 52-week range. Alert conditions that need those
	 * (PCT_CHANGE, VOLUME_SPIKE, WEEK52_HIGH_NEAR, WEEK52_LOW_NEAR) simply never fire
	 * against the real API — only PRICE_ABOVE/PRICE_BELOW work — until an endpoint
	 * carrying that data is found. Mock mode still supports all six.
	 */
	@Override
	public Quote getQuote(String symbol, Market market) {
		// The docs' own getting-started example uses a plural "symbols" query param
		// (GET /api/v1/stocks?symbols=005930) for a single symbol, so /api/v1/prices is
		// called the same way and the (single-element) result list is unwrapped here.
		List<QuoteDto> dtos = unwrap(authorizedGet(PRICES_PATH, PricesEnvelope.class,
				uri -> uri.queryParam("symbols", symbol), false));
		if (dtos.isEmpty()) {
			throw new IllegalStateException("시세 응답이 비어있습니다: " + symbol);
		}
		QuoteDto dto = dtos.get(0);
		return new Quote(
				symbol, market, dto.lastPrice(), null, 0, 0, null, null,
				dto.timestamp() != null ? dto.timestamp() : java.time.Instant.now());
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>{@code symbol} (singular) and {@code interval} are confirmed to be the right
	 * parameter names — with them the API rejects the request as "지원하지 않는 캔들 주기"
	 * (known field, unsupported value) rather than "요청 필드가 올바르지 않습니다" (unknown
	 * field). {@link #DAILY_INTERVAL} is still a guess at the accepted value; probe it with
	 * {@code GET /api/toss/probe/candle-intervals?symbol=005930}.
	 */
	@Override
	public List<Candle> getDailyCandles(String symbol, Market market, int days) {
		return unwrap(authorizedGet(CANDLES_PATH, CandlesEnvelope.class, uri -> uri
				.queryParam("symbol", symbol)
				.queryParam("interval", DAILY_INTERVAL)
				.queryParam("count", days), false)).stream()
				.map(d -> new Candle(d.date(), d.open(), d.high(), d.low(), d.close(), d.volume()))
				.toList();
	}

	@Override
	public List<Holding> getHoldings() {
		return toHoldings(fetchHoldings());
	}

	/** Package-private and static so {@code TossApiResponseMappingTest} can pin it to a real response. */
	static List<Holding> toHoldings(HoldingsResult result) {
		if (result.items() == null) {
			return List.of();
		}
		return result.items().stream()
				.map(i -> new Holding(
						i.symbol(), toMarket(i.marketCountry()), i.name(),
						i.quantity(), i.averagePurchasePrice(), i.lastPrice()))
				.toList();
	}

	private HoldingsResult fetchHoldings() {
		HoldingsEnvelope envelope = authorizedGet(HOLDINGS_PATH, HoldingsEnvelope.class, uri -> uri, true);
		if (envelope == null || envelope.result() == null) {
			throw new IllegalStateException("보유종목 응답이 비어있습니다.");
		}
		return envelope.result();
	}

	/** {@code marketCountry} is an ISO country code ("KR"/"US") rather than our market name. */
	private static Market toMarket(String marketCountry) {
		return "US".equalsIgnoreCase(marketCountry) ? Market.US : Market.KR;
	}

	private static <T> List<T> unwrap(ResultEnvelope<T> envelope) {
		return envelope == null || envelope.result() == null ? List.of() : envelope.result();
	}

	/**
	 * Raw {@code GET /api/v1/accounts} JSON, used by {@code TossDiagnosticsController} to
	 * find the {@code accountSeq} for {@link TossApiProperties#accountSeq()}.
	 */
	public JsonNode getAccountsRaw() {
		return authorizedGet(ACCOUNTS_PATH, JsonNode.class, uri -> uri, false);
	}

	/** Raw {@code GET /api/v1/holdings} JSON — for confirming {@link HoldingDto}'s field names. */
	public JsonNode getHoldingsRaw() {
		return authorizedGet(HOLDINGS_PATH, JsonNode.class, uri -> uri, true);
	}

	/** Raw {@code GET /api/v1/prices} JSON — for confirming {@link QuoteDto}'s field names. */
	public JsonNode getPricesRaw(String symbol) {
		return authorizedGet(PRICES_PATH, JsonNode.class, uri -> uri.queryParam("symbols", symbol), false);
	}

	/** Raw {@code GET /api/v1/candles} JSON — for confirming {@link CandleDto}'s field names. */
	public JsonNode getCandlesRaw(String symbol, int days) {
		return authorizedGet(CANDLES_PATH, JsonNode.class, uri -> uri
				.queryParam("symbol", symbol)
				.queryParam("interval", DAILY_INTERVAL)
				.queryParam("count", days), false);
	}

	/**
	 * Raw GET against an arbitrary {@code /api/v1/**} path with arbitrary query params —
	 * a development aid for the endpoints whose request/response schemas the docs don't
	 * pin down (e.g. finding the parameter names {@code /api/v1/candles} actually wants,
	 * which currently answers {@code invalid-request}). The account header is sent when
	 * {@code accountSeq} is configured.
	 *
	 * @param path must start with {@code /api/v1/} — this is a debugging window into the
	 *             Toss API, not a general-purpose proxy
	 */
	public JsonNode getRaw(String path, Map<String, String> queryParams) {
		if (!path.startsWith("/api/v1/")) {
			throw new IllegalArgumentException("path는 /api/v1/ 로 시작해야 합니다: " + path);
		}
		String accountSeq = properties.accountSeq();
		return authorizedGet(path, JsonNode.class,
				uri -> {
					UriBuilder built = uri;
					for (Map.Entry<String, String> param : queryParams.entrySet()) {
						built = built.queryParam(param.getKey(), param.getValue());
					}
					return built;
				},
				accountSeq != null && !accountSeq.isBlank());
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>The holdings response already carries account-level aggregates, so this reads them
	 * straight off it — one call, and a genuinely daily P&amp;L figure rather than the
	 * total-vs-cost approximation this used before the response shape was known. Totals are
	 * taken in KRW; the API reports each block in both KRW and USD.
	 */
	@Override
	public AccountSummary getAccountSummary() {
		return toAccountSummary(fetchHoldings());
	}

	/** Package-private and static so {@code TossApiResponseMappingTest} can pin it to a real response. */
	static AccountSummary toAccountSummary(HoldingsResult result) {
		BigDecimal totalValue = krwOrZero(result.marketValue() == null ? null : result.marketValue().amount());
		BigDecimal dailyPnl = krwOrZero(result.dailyProfitLoss() == null ? null : result.dailyProfitLoss().amount());
		// The API reports rates as fractions ("-0.0026"); our AccountSummary is in percent.
		BigDecimal rate = result.dailyProfitLoss() == null ? null : result.dailyProfitLoss().rate();
		BigDecimal dailyPnlRate = rate == null
				? BigDecimal.ZERO
				: rate.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
		return new AccountSummary(
				totalValue.setScale(2, RoundingMode.HALF_UP), dailyPnl.setScale(2, RoundingMode.HALF_UP), dailyPnlRate);
	}

	private static BigDecimal krwOrZero(CurrencyAmount amount) {
		return amount == null || amount.krw() == null ? BigDecimal.ZERO : amount.krw();
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

	/**
	 * Every endpoint observed so far wraps its payload in a {@code {"result": ...}} envelope
	 * (confirmed against the real {@code GET /api/v1/accounts} response), so each list
	 * endpoint below unwraps one of these rather than binding an array directly.
	 */
	private interface ResultEnvelope<T> {
		List<T> result();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record PricesEnvelope(List<QuoteDto> result) implements ResultEnvelope<QuoteDto> {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record CandlesEnvelope(List<CandleDto> result) implements ResultEnvelope<CandleDto> {
	}

	/**
	 * Holdings is the one endpoint whose {@code result} is an object rather than a list:
	 * account-level aggregates plus an {@code items} array. Confirmed against a real
	 * response; every monetary value arrives as a JSON string, and every {@code rate} is a
	 * fraction ({@code "-0.0315"} = -3.15%).
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record HoldingsEnvelope(HoldingsResult result) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record HoldingsResult(
			CurrencyAmount totalPurchaseAmount,
			ValueBlock marketValue,
			PnlBlock profitLoss,
			PnlBlock dailyProfitLoss,
			List<HoldingDto> items) {
	}

	/** The API reports each account-level figure in both currencies. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record CurrencyAmount(BigDecimal krw, BigDecimal usd) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ValueBlock(CurrencyAmount amount, CurrencyAmount amountAfterCost) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record PnlBlock(CurrencyAmount amount, BigDecimal rate) {
	}

	/**
	 * Confirmed against a real {@code GET /api/v1/prices} response, which carries only the
	 * last traded price:
	 * <pre>{"symbol":"005930","timestamp":"2026-08-28T19:59:59.000+09:00","lastPrice":"256500","currency":"KRW"}</pre>
	 * Note {@code lastPrice} arrives as a JSON string, which Jackson coerces to
	 * {@link BigDecimal}. There is no change rate, volume or 52-week range here — see
	 * {@link #getQuote} for what that costs the alert conditions.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record QuoteDto(
			String symbol,
			@JsonProperty("lastPrice") BigDecimal lastPrice,
			String currency,
			Instant timestamp) {
	}

	/** One entry of {@code result.items}; {@code marketCountry} is "KR"/"US". */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record HoldingDto(
			String symbol,
			String name,
			String marketCountry,
			String currency,
			BigDecimal quantity,
			BigDecimal lastPrice,
			BigDecimal averagePurchasePrice) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record CandleDto(LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {
	}
}
