package com.stockmonitor.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.stockmonitor.config.TossApiProperties;
import com.stockmonitor.external.toss.TossHttpApiClient;
import com.stockmonitor.external.toss.TossOAuthTokenProvider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets you check that {@code TOSS_CLIENT_ID}/{@code TOSS_CLIENT_SECRET} and the OAuth
 * token endpoint actually work, independent of whether {@link com.stockmonitor.external.toss.TossHttpApiClient}'s
 * guessed data-endpoint paths are correct — useful the moment real credentials are
 * added, before touching anything else.
 */
@RestController
@RequestMapping("/api/toss")
public class TossDiagnosticsController {

	private final ObjectProvider<TossOAuthTokenProvider> tokenProviderProvider;
	private final ObjectProvider<TossHttpApiClient> httpApiClientProvider;
	private final TossApiProperties properties;

	public TossDiagnosticsController(
			ObjectProvider<TossOAuthTokenProvider> tokenProviderProvider,
			ObjectProvider<TossHttpApiClient> httpApiClientProvider,
			TossApiProperties properties) {
		this.tokenProviderProvider = tokenProviderProvider;
		this.httpApiClientProvider = httpApiClientProvider;
		this.properties = properties;
	}

	@PostMapping("/verify-connection")
	public Map<String, Object> verifyConnection() {
		if (!properties.useRealClient()) {
			return Map.of("ok", false, "message", "toss.api.use-real-client=false 입니다. TOSS_API_USE_REAL_CLIENT=true로 켠 뒤 다시 시도하세요.");
		}
		TossOAuthTokenProvider provider = tokenProviderProvider.getIfAvailable();
		if (provider == null) {
			return Map.of("ok", false, "message", "TossOAuthTokenProvider 빈을 찾을 수 없습니다.");
		}
		try {
			String token = provider.getAccessToken();
			return Map.of("ok", true, "message", "토큰 발급 성공 (길이 %d자)".formatted(token.length()));
		} catch (Exception e) {
			return Map.of("ok", false, "message", "토큰 발급 실패: " + e.getMessage());
		}
	}

	/**
	 * Raw {@code GET /api/v1/accounts} response — use this to find the correct
	 * {@code accountSeq} for {@code TOSS_ACCOUNT_SEQ} (e.g. after an "account-not-found"
	 * error from the holdings/account-scoped endpoints). Requires only a valid token, not
	 * {@code accountSeq} itself, since that's exactly what this discovers.
	 */
	@GetMapping("/accounts")
	public Map<String, Object> accounts() {
		return raw("계좌 목록", TossHttpApiClient::getAccountsRaw);
	}

	/**
	 * Raw {@code /api/v1/holdings} / {@code /prices} / {@code /candles} responses. These
	 * endpoints' field names aren't confirmed by the docs, so these let you see exactly
	 * what comes back and line the DTOs in {@link TossHttpApiClient} up against it.
	 */
	@GetMapping("/raw/holdings")
	public Map<String, Object> rawHoldings() {
		return raw("보유종목", TossHttpApiClient::getHoldingsRaw);
	}

	@GetMapping("/raw/prices")
	public Map<String, Object> rawPrices(@RequestParam String symbol) {
		return raw("시세", client -> client.getPricesRaw(symbol));
	}

	@GetMapping("/raw/candles")
	public Map<String, Object> rawCandles(@RequestParam String symbol, @RequestParam(defaultValue = "5") int days) {
		return raw("캔들", client -> client.getCandlesRaw(symbol, days));
	}

	/**
	 * Free-form GET against any {@code /api/v1/**} path, forwarding every query param except
	 * {@code path}. For probing endpoints whose parameter names the docs don't pin down —
	 * e.g. {@code /api/toss/raw?path=/api/v1/candles&symbols=005930&period=DAY}.
	 */
	@GetMapping("/raw")
	public Map<String, Object> rawPath(@RequestParam String path, @RequestParam Map<String, String> allParams) {
		Map<String, String> queryParams = new LinkedHashMap<>(allParams);
		queryParams.remove("path");
		return raw("요청(" + path + ")", client -> client.getRaw(path, queryParams));
	}

	private Map<String, Object> raw(String label, Function<TossHttpApiClient, JsonNode> call) {
		if (!properties.useRealClient()) {
			return Map.of("ok", false, "message", "toss.api.use-real-client=false 입니다. TOSS_API_USE_REAL_CLIENT=true로 켠 뒤 다시 시도하세요.");
		}
		TossHttpApiClient client = httpApiClientProvider.getIfAvailable();
		if (client == null) {
			return Map.of("ok", false, "message", "TossHttpApiClient 빈을 찾을 수 없습니다.");
		}
		try {
			return Map.of("ok", true, "response", call.apply(client));
		} catch (Exception e) {
			return Map.of("ok", false, "message", label + " 조회 실패: " + e.getMessage());
		}
	}
}
