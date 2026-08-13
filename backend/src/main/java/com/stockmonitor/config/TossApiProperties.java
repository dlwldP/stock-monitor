package com.stockmonitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds to {@code toss.api.*}, which read from {@code TOSS_CLIENT_ID}/{@code TOSS_CLIENT_SECRET}
 * (see README). {@code useRealClient} defaults to {@code false} so the app keeps working on
 * {@link com.stockmonitor.external.toss.MockTossApiClient} even after credentials are added —
 * flip it once {@link com.stockmonitor.external.toss.TossHttpApiClient}'s guessed endpoint
 * paths have been verified/fixed against the real API docs (see that class's Javadoc).
 */
@ConfigurationProperties(prefix = "toss.api")
public record TossApiProperties(String baseUrl, String clientId, String clientSecret, boolean useRealClient) {
}
