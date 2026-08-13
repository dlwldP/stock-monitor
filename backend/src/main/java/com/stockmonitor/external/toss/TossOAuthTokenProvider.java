package com.stockmonitor.external.toss;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.stockmonitor.config.TossApiProperties;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * OAuth 2.0 client-credentials token management for the Toss Securities Open API
 * (docs/PLANNING.md section 5): {@code POST /oauth2/token}, ~1 hour expiry, auto-refresh
 * before it lapses. This part follows the documented spec fairly literally and is the
 * piece most likely to be correct as-is; {@link TossHttpApiClient}'s actual data
 * endpoints are the ones that need verifying once real docs are available.
 *
 * <p>Assumes a standard OAuth2 client-credentials token response
 * ({@code access_token}/{@code token_type}/{@code expires_in}, RFC 6749) sent as
 * {@code application/x-www-form-urlencoded}. Adjust {@link #fetchToken()} if the real
 * API differs (e.g. JSON body, different field names, Basic-auth for client
 * credentials instead of form fields).
 */
public class TossOAuthTokenProvider {

	private static final int REFRESH_BUFFER_SECONDS = 60;
	private static final int CONNECT_TIMEOUT_MS = 5_000;
	private static final int READ_TIMEOUT_MS = 10_000;

	private final TossApiProperties properties;
	private final RestClient restClient;

	private volatile CachedToken cached;

	public TossOAuthTokenProvider(TossApiProperties properties) {
		this.properties = properties;

		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
		requestFactory.setReadTimeout(READ_TIMEOUT_MS);
		this.restClient = RestClient.builder().requestFactory(requestFactory).build();
	}

	/** Returns a valid access token, fetching or refreshing it first if needed. */
	public synchronized String getAccessToken() {
		if (cached == null || Instant.now().isAfter(cached.expiresAt.minusSeconds(REFRESH_BUFFER_SECONDS))) {
			cached = fetchToken();
		}
		return cached.accessToken;
	}

	private CachedToken fetchToken() {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "client_credentials");
		form.add("client_id", properties.clientId());
		form.add("client_secret", properties.clientSecret());

		TokenResponse response = restClient.post()
				.uri(properties.baseUrl() + "/oauth2/token")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(TokenResponse.class);

		if (response == null || response.accessToken() == null) {
			throw new IllegalStateException("토스증권 OAuth 토큰 발급 응답이 비어있습니다.");
		}
		return new CachedToken(response.accessToken(), Instant.now().plusSeconds(response.expiresIn()));
	}

	private record CachedToken(String accessToken, Instant expiresAt) {
	}

	private record TokenResponse(
			@JsonProperty("access_token") String accessToken,
			@JsonProperty("token_type") String tokenType,
			@JsonProperty("expires_in") long expiresIn) {
	}
}
