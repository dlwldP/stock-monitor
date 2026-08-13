package com.stockmonitor.config;

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
 * {@link TossHttpApiClient} once its endpoint paths have been verified against the
 * real API docs (see that class's Javadoc — they're unverified placeholders today).
 */
@Configuration
public class TossApiClientConfig {

	@Bean
	@ConditionalOnProperty(prefix = "toss.api", name = "use-real-client", havingValue = "true")
	public TossOAuthTokenProvider tossOAuthTokenProvider(TossApiProperties properties) {
		return new TossOAuthTokenProvider(properties);
	}

	@Bean
	@ConditionalOnProperty(prefix = "toss.api", name = "use-real-client", havingValue = "true")
	public TossApiClient tossHttpApiClient(TossApiProperties properties, TossOAuthTokenProvider tokenProvider) {
		return new TossHttpApiClient(properties, tokenProvider);
	}

	@Bean
	@ConditionalOnProperty(prefix = "toss.api", name = "use-real-client", havingValue = "false", matchIfMissing = true)
	public TossApiClient mockTossApiClient() {
		return new MockTossApiClient();
	}
}
