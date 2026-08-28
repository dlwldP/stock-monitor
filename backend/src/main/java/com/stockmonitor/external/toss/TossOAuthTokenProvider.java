package com.stockmonitor.external.toss;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockmonitor.config.TossApiProperties;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * OAuth 2.0 client-credentials token management for the Toss Securities Open API
 * (confirmed against https://developers.tossinvest.com/docs "시작하기" section):
 * {@code POST /oauth2/token}, form-urlencoded {@code grant_type=client_credentials}
 * + {@code client_id} + {@code client_secret}, auto-refreshed before it expires.
 */
public class TossOAuthTokenProvider {

	private static final int REFRESH_BUFFER_SECONDS = 60;
	private static final int CONNECT_TIMEOUT_MS = 5_000;
	private static final int READ_TIMEOUT_MS = 10_000;

	private final TossApiProperties properties;
	private final ObjectMapper objectMapper;
	private final RestClient restClient;

	private volatile CachedToken cached;

	public TossOAuthTokenProvider(TossApiProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;

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
				.onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
					int status = httpResponse.getStatusCode().value();
					String body = TossErrorBodyReader.readAsText(httpResponse);
					try {
						TossErrorEnvelope envelope = objectMapper.readValue(body, TossErrorEnvelope.class);
						throw new TossApiException(status, envelope.error().code(), envelope.error().message(), envelope.error().requestId());
					} catch (IOException e) {
						throw new TossApiException(status, "unknown",
								"토큰 발급 에러 응답을 파싱할 수 없습니다 (HTTP " + status + "). 응답 본문: "
										+ snippet(body) + " — client_id/secret이 올바른지, WTS Open API 설정의 허용 IP에 서버 IP가 등록됐는지 확인하세요.",
								null);
					}
				})
				.body(TokenResponse.class);

		if (response == null || response.accessToken() == null) {
			throw new IllegalStateException("토스증권 OAuth 토큰 발급 응답이 비어있습니다.");
		}
		return new CachedToken(response.accessToken(), Instant.now().plusSeconds(response.expiresIn()));
	}

	private static String snippet(String body) {
		String trimmed = body.strip();
		if (trimmed.isEmpty()) {
			return "(empty)";
		}
		return trimmed.length() > 300 ? trimmed.substring(0, 300) + "..." : trimmed;
	}

	private record CachedToken(String accessToken, Instant expiresAt) {
	}

	private record TokenResponse(
			@JsonProperty("access_token") String accessToken,
			@JsonProperty("token_type") String tokenType,
			@JsonProperty("expires_in") long expiresIn) {
	}
}
