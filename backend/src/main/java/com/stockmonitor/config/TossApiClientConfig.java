package com.stockmonitor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockmonitor.external.toss.MockTossApiClient;
import com.stockmonitor.external.toss.TossApiClient;
import com.stockmonitor.external.toss.TossHttpApiClient;
import com.stockmonitor.external.toss.TossOAuthTokenProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks which {@link TossApiClient} bean serves the app. Defaults to
 * {@link MockTossApiClient} — set {@code TOSS_API_USE_REAL_CLIENT=true} (alongside
 * {@code TOSS_CLIENT_ID}/{@code TOSS_CLIENT_SECRET}) to switch to
 * {@link TossHttpApiClient} once its response DTOs have been verified against the
 * real API docs (see that class's Javadoc — endpoint paths are confirmed from
 * https://developers.tossinvest.com/docs, but exact response field names aren't yet).
 */
@Configuration
public class TossApiClientConfig {

	@Bean
	@ConditionalOnProperty(prefix = "toss.api", name = "use-real-client", havingValue = "true")
	public TossOAuthTokenProvider tossOAuthTokenProvider(TossApiProperties properties, ObjectMapper objectMapper) {
		return new TossOAuthTokenProvider(properties, objectMapper);
	}

	@Bean
	@ConditionalOnProperty(prefix = "toss.api", name = "use-real-client", havingValue = "true")
	public TossApiClient tossHttpApiClient(TossApiProperties properties, TossOAuthTokenProvider tokenProvider, ObjectMapper objectMapper) {
		return new TossHttpApiClient(properties, tokenProvider, objectMapper);
	}

	@Bean
	@ConditionalOnProperty(prefix = "toss.api", name = "use-real-client", havingValue = "false", matchIfMissing = true)
	public TossApiClient mockTossApiClient() {
		return new MockTossApiClient();
	}
}
