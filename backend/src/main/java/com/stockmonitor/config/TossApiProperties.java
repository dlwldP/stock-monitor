package com.stockmonitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds to {@code toss.api.*}, which read from {@code TOSS_CLIENT_ID}/{@code TOSS_CLIENT_SECRET}
 * (see README). {@code useRealClient} defaults to {@code false} so the app keeps working on
 * {@link com.stockmonitor.external.toss.MockTossApiClient} even after credentials are added —
 * flip it once {@link com.stockmonitor.external.toss.TossHttpApiClient}'s response DTOs have
 * been verified/fixed against the real API docs (see that class's Javadoc).
 *
 * @param accountSeq the {@code accountSeq} sent as the {@code X-Tossinvest-Account} header for
 *                    Account/Asset/Order calls. Find it via {@code GET /api/v1/accounts} once
 *                    real credentials are set — not auto-discovered, since this app assumes a
 *                    single account (see README's single-user note).
 */
@ConfigurationProperties(prefix = "toss.api")
public record TossApiProperties(String baseUrl, String clientId, String clientSecret, String accountSeq, boolean useRealClient) {
}
