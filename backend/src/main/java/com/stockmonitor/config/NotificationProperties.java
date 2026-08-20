package com.stockmonitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds to the {@code notification.*} keys in application.yml, which in turn read
 * from environment variables. Single global webhook/recipient for MVP — the
 * docs/PLANNING.md section 8 "설정" screen is read-only (see SettingsController),
 * values are still only ever set via env vars, never stored in the DB.
 */
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(String discordWebhookUrl, String emailTo) {
}
