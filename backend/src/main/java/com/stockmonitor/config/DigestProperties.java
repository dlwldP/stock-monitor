package com.stockmonitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Binds to {@code digest.*} — the daily summary email (docs/PLANNING.md section 10, stage 3). */
@ConfigurationProperties(prefix = "digest")
public record DigestProperties(boolean enabled, String cron) {
}
