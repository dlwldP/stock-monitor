package com.stockmonitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds to {@code retention.*} — how long the two tables that grow on a timer are kept.
 *
 * @param alertLogDays        delete alert logs older than this. They're an audit trail of
 *                            notifications already delivered, so old ones stop being useful.
 * @param snapshotKeepAllDays keep every account snapshot for this many days. Beyond it,
 *                            snapshots are thinned to one per day rather than deleted — the
 *                            자산 추이 chart's long-term shape is the point of collecting them,
 *                            so the history stays, just at daily resolution instead of every
 *                            15 minutes.
 */
@ConfigurationProperties(prefix = "retention")
public record RetentionProperties(boolean enabled, String cron, int alertLogDays, int snapshotKeepAllDays) {
}
