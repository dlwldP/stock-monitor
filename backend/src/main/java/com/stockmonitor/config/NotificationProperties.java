package com.stockmonitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds to the {@code notification.*} keys in application.yml, which in turn read
 * from environment variables. Single global webhook for MVP (no per-user settings
 * screen yet — see docs/PLANNING.md section 8, item 5).
 */
@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(String discordWebhookUrl) {
}
