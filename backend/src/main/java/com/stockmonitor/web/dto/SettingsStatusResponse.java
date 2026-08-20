package com.stockmonitor.web.dto;

/**
 * Read-only view of which env-var-backed settings are configured (docs/PLANNING.md
 * section 8, item 5 — "설정" screen). Never exposes the actual secret values, only
 * whether each is set, since nothing is stored in the DB (see SettingsController).
 */
public record SettingsStatusResponse(TossStatus toss, NotificationStatus notification, DigestStatus digest) {

	public record TossStatus(boolean clientIdSet, boolean clientSecretSet, boolean accountSeqSet, boolean useRealClient) {
	}

	public record NotificationStatus(boolean discordWebhookSet, boolean smtpConfigured, boolean emailToSet) {
	}

	public record DigestStatus(boolean enabled, String cron) {
	}
}
