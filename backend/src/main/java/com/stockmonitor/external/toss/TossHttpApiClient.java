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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
 * <p>{@code /api/v1/candles} was pinned down the same way — see {@link #getDailyCandles} for
 * its parameters and {@link CandleDto} for its fields. The {@code getRaw*} methods here
 * (exposed as {@code GET /api/toss/raw/*}, plus a free-form {@code GET /api/toss/raw?path=...})
 * dump untouched JSON, which is how each of these was worked out and how the next one can be.
 *
 * <p>{@code /api/v1/prices} alone doesn't carry change rate, volume or a 52-week range, which
 * four of the six alert conditions need — {@link #getQuote} derives them from
 * {@link #getDailyCandles} instead of leaving those conditions permanently unsupported; see its
 * Javadoc for what that derivation can and can't guarantee.
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
	/** The one daily-interval spelling the API accepts — see {@link #getDailyCandles}. */
	private static final String DAILY_INTERVAL = "1d";
	private static final long DEFAULT_RETRY_SECONDS = 2;
	/** Best-effort candle window for deriving change rate/volume/52-week range — see {@link #getQuote}. */
	private static final int CANDLE_LOOKBACK_DAYS = 400;
	private static final int AVG_VOLUME_WINDOW = 20;
	private static final long CANDLE_CACHE_TTL_MINUTES = 30;

	private final Map<String, CachedCandles> candleCache = new ConcurrentHashMap<>();

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
	 * {@link QuoteDto}), so change rate, volume and the 52-week range aren't in this
	 * response. Rather than leave the four alert conditions that need them permanently
	 * unsupported in real mode, this derives them from {@link #getDailyCandles} — which
	 * already has daily high/low/volume/close confirmed working:
	 *
	 * <ul>
	 *   <li>{@code changeRate}: {@code lastPrice} vs. the close of the most recent candle
	 *       strictly before today.
	 *   <li>{@code volume}/{@code avgVolume}: the most recent candle's volume, against the
	 *       average of the trailing {@value #AVG_VOLUME_WINDOW} prior candles — "the most
	 *       recent complete day's volume vs. a trailing baseline", since nothing confirmed
	 *       here carries live intraday volume the way a broker terminal would.
	 *   <li>{@code week52High}/{@code week52Low}: the max/min across whatever candle history
	 *       {@link #getDailyCandles} returns (capped at {@value #CANDLE_LOOKBACK_DAYS} days),
	 *       widened by {@code lastPrice} in case today is itself a new extreme. This is a
	 *       best-effort window, not a guaranteed 252 trading days — the real API doesn't take
	 *       a count parameter (see {@link #getDailyCandles}), so how far back its default
	 *       history goes is whatever it happens to return; use {@code GET /api/toss/raw/candles}
	 *       to check for a given symbol.
	 * </ul>
	 *
	 * <p>Candle history is cached per symbol for {@value #CANDLE_CACHE_TTL_MINUTES} minutes
	 * (it moves once a day, not once a minute) so this doesn't double the API traffic of every
	 * {@link com.stockmonitor.scheduler.PriceAlertScheduler} tick. If fetching it fails for any
	 * reason, this quote still returns with the derived fields left at their empty defaults
	 * rather than failing the whole quote — {@code PRICE_ABOVE}/{@code PRICE_BELOW} shouldn't
	 * go down because the candles call had a bad moment.
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
		OffsetDateTime timestamp = dto.parsedTimestamp();
		QuoteEnrichment enrichment = enrich(symbol, market, dto.lastPrice(), timestamp.toLocalDate());
		return new Quote(
				symbol, market, dto.lastPrice(), enrichment.changeRate(), enrichment.volume(),
				enrichment.avgVolume(), enrichment.week52High(), enrichment.week52Low(), timestamp.toInstant());
	}

	private QuoteEnrichment enrich(String symbol, Market market, BigDecimal lastPrice, LocalDate quoteDate) {
		List<Candle> candles;
		try {
			candles = cachedDailyCandles(symbol, market);
		} catch (RuntimeException e) {
			log.warn("Could not derive change rate/volume/52-week range for {} from candles: {}", symbol, e.getMessage());
			return QuoteEnrichment.EMPTY;
		}
		return computeEnrichment(candles, lastPrice, quoteDate);
	}

	/**
	 * Package-private, static and pure (no network) so {@code TossApiResponseMappingTest} can
	 * exercise the derivation directly — see {@link #getQuote}'s Javadoc for what each field
	 * means and why.
	 */
	static QuoteEnrichment computeEnrichment(List<Candle> candles, BigDecimal lastPrice, LocalDate quoteDate) {
		if (candles.isEmpty()) {
			return QuoteEnrichment.EMPTY;
		}

		BigDecimal week52High = candles.stream().map(Candle::high).max(Comparator.naturalOrder()).get().max(lastPrice);
		BigDecimal week52Low = candles.stream().map(Candle::low).min(Comparator.naturalOrder()).get().min(lastPrice);

		List<Candle> priorDays = candles.stream().filter(c -> c.date().isBefore(quoteDate)).toList();
		BigDecimal changeRate = null;
		if (!priorDays.isEmpty()) {
			BigDecimal previousClose = priorDays.get(priorDays.size() - 1).close();
			if (previousClose.signum() != 0) {
				changeRate = lastPrice.subtract(previousClose)
						.divide(previousClose, 6, RoundingMode.HALF_UP)
						.multiply(BigDecimal.valueOf(100))
						.setScale(2, RoundingMode.HALF_UP);
			}
		}

		// Deliberately structural (last candle vs. everything before it) rather than
		// quoteDate-based like changeRate above: candles.getLast() is "the most recent day we
		// have," whether or not the live quote's own trading day happens to match it, and
		// baselining against every candle up to but not including it avoids double-counting
		// that same day into its own average.
		long volume = candles.get(candles.size() - 1).volume();
		List<Candle> volumeBaseline = candles.size() > 1 ? candles.subList(0, candles.size() - 1) : List.of();
		List<Candle> avgWindow = volumeBaseline.size() > AVG_VOLUME_WINDOW
				? volumeBaseline.subList(volumeBaseline.size() - AVG_VOLUME_WINDOW, volumeBaseline.size())
				: volumeBaseline;
		long avgVolume = avgWindow.isEmpty() ? 0 : (long) avgWindow.stream().mapToLong(Candle::volume).average().orElse(0);

		return new QuoteEnrichment(changeRate, volume, avgVolume, week52High, week52Low);
	}

	/** Best-effort derived fields for {@link #getQuote} — see its Javadoc for how each is computed. */
	record QuoteEnrichment(BigDecimal changeRate, long volume, long avgVolume, BigDecimal week52High, BigDecimal week52Low) {
		static final QuoteEnrichment EMPTY = new QuoteEnrichment(null, 0, 0, null, null);
	}

	private List<Candle> cachedDailyCandles(String symbol, Market market) {
		String key = market + ":" + symbol;
		CachedCandles cached = candleCache.get(key);
		if (cached != null && Duration.between(cached.fetchedAt(), Instant.now()).toMinutes() < CANDLE_CACHE_TTL_MINUTES) {
			return cached.candles();
		}
		List<Candle> fresh = getDailyCandles(symbol, market, CANDLE_LOOKBACK_DAYS);
		candleCache.put(key, new CachedCandles(fresh, Instant.now()));
		return fresh;
	}

	private record CachedCandles(List<Candle> candles, Instant fetchedAt) {
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>Confirmed by probing the live API: the parameters are {@code symbol} (singular) and
	 * {@code interval}, and {@code "1d"} is the accepted daily interval (every other
	 * spelling tried — {@code D}, {@code DAY}, {@code DAY_1}, {@code P1D}, … — is rejected
	 * with "지원하지 않는 캔들 주기입니다").
	 *
	 * <p>No {@code count}/limit parameter is sent because none is confirmed and an
	 * unrecognized field fails the whole request; the API returns a long history by default,
	 * which is trimmed to {@code days} here. The response also carries a {@code nextBefore}
	 * cursor for paging further back, which isn't used — if a chart ever needs more history
	 * than one page holds, that's the hook for it.
	 */
	@Override
	public List<Candle> getDailyCandles(String symbol, Market market, int days) {
		JsonNode result = authorizedGet(CANDLES_PATH, JsonNode.class, uri -> uri
				.queryParam("symbol", symbol)
				.queryParam("interval", DAILY_INTERVAL), false)
				.path("result");
		return toCandles(result, symbol, days, objectMapper);
	}

	/** Package-private and static so {@code TossApiResponseMappingTest} can pin it to a real response. */
	static List<Candle> toCandles(JsonNode result, String symbol, int days, ObjectMapper objectMapper) {
		List<Candle> candles = new ArrayList<>();
		for (JsonNode node : candleArray(result, symbol)) {
			CandleDto dto = objectMapper.convertValue(node, CandleDto.class);
			candles.add(new Candle(
					dto.tradingDate(), dto.openPrice(), dto.highPrice(),
					dto.lowPrice(), dto.closePrice(), dto.volume()));
		}
		// The API returns newest-first; charts (and MockTossApiClient) work oldest-first.
		candles.sort(Comparator.comparing(Candle::date));
		return candles.size() <= days ? candles : candles.subList(candles.size() - days, candles.size());
	}

	/**
	 * Picks the candle array out of the response's {@code result} object, which also holds a
	 * {@code nextBefore} cursor. The array's field name isn't documented and wasn't visible
	 * in the captured response, so rather than guess a name and render an empty chart when
	 * the guess is wrong, this takes the object's sole array field and fails loudly, naming
	 * what it actually found, if there isn't one.
	 */
	private static JsonNode candleArray(JsonNode result, String symbol) {
		if (result.isArray()) {
			return result;
		}
		for (Iterator<Map.Entry<String, JsonNode>> it = result.fields(); it.hasNext(); ) {
			Map.Entry<String, JsonNode> field = it.next();
			if (field.getValue().isArray()) {
				return field.getValue();
			}
		}
		List<String> fieldNames = new ArrayList<>();
		result.fieldNames().forEachRemaining(fieldNames::add);
		throw new IllegalStateException(
				"캔들 응답에서 배열 필드를 찾지 못했습니다 (symbol=%s). result의 필드: %s".formatted(symbol, fieldNames));
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
	 * <pre>{"symbol":"000000","timestamp":"2026-08-28T19:59:59.000+09:00","lastPrice":"48000","currency":"KRW"}</pre>
	 * Note {@code lastPrice} arrives as a JSON string, which Jackson coerces to
	 * {@link BigDecimal}. There's no change rate, volume or 52-week range in this endpoint
	 * itself — {@link #getQuote} derives those from {@link #getDailyCandles}.
	 *
	 * <p>{@code timestamp} is kept as a String and parsed with the offset preserved, the same
	 * reason as {@link CandleDto}: binding it as {@code Instant}/{@code OffsetDateTime} would
	 * let Jackson's {@code ADJUST_DATES_TO_CONTEXT_TIME_ZONE} normalize it to UTC before this
	 * code ever sees it, and the trading-day math below needs the market's own calendar day.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record QuoteDto(
			String symbol,
			@JsonProperty("lastPrice") BigDecimal lastPrice,
			String currency,
			String timestamp) {

		OffsetDateTime parsedTimestamp() {
			return timestamp != null ? OffsetDateTime.parse(timestamp) : OffsetDateTime.now();
		}
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

	/**
	 * Confirmed against a real {@code GET /api/v1/candles?symbol=...&interval=1d} entry:
	 * <pre>{"timestamp":"2026-06-08T00:00:00.000+09:00","openPrice":"47000","highPrice":"48500",
	 * "lowPrice":"46500","closePrice":"48000","volume":"1200000","currency":"KRW"}</pre>
	 * The timestamp carries the market's own offset, so its local date is the trading day.
	 */
	@JsonIgnoreProperties(ignoreUnknown = true)
	record CandleDto(
			String timestamp,
			BigDecimal openPrice,
			BigDecimal highPrice,
			BigDecimal lowPrice,
			BigDecimal closePrice,
			long volume,
			String currency) {

		/**
		 * The trading day, read in the offset the API sent.
		 *
		 * <p>Kept as a String and parsed here rather than bound as an {@code OffsetDateTime}:
		 * Jackson's {@code ADJUST_DATES_TO_CONTEXT_TIME_ZONE} (on by default, including in
		 * Spring Boot's ObjectMapper) would normalize these midnight-KST timestamps to UTC,
		 * putting every Korean candle on the previous day.
		 */
		LocalDate tradingDate() {
			return OffsetDateTime.parse(timestamp).toLocalDate();
		}
	}
}
