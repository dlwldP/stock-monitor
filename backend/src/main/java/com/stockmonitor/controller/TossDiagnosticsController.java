package com.stockmonitor.controller;

import com.stockmonitor.config.TossApiProperties;
import com.stockmonitor.external.toss.TossHttpApiClient;
import com.stockmonitor.external.toss.TossOAuthTokenProvider;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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
		if (!properties.useRealClient()) {
			return Map.of("ok", false, "message", "toss.api.use-real-client=false 입니다. TOSS_API_USE_REAL_CLIENT=true로 켠 뒤 다시 시도하세요.");
		}
		TossHttpApiClient client = httpApiClientProvider.getIfAvailable();
		if (client == null) {
			return Map.of("ok", false, "message", "TossHttpApiClient 빈을 찾을 수 없습니다.");
		}
		try {
			return Map.of("ok", true, "accounts", client.getAccountsRaw());
		} catch (Exception e) {
			return Map.of("ok", false, "message", "계좌 목록 조회 실패: " + e.getMessage());
		}
	}
}
