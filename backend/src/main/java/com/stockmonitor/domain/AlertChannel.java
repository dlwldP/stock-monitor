package com.stockmonitor.domain;

/**
 * Notification channels. EMAIL is part of the target design (docs/PLANNING.md section 6)
 * but its adapter ships in stage 2 — selecting it in an alert rule is rejected for now.
 */
public enum AlertChannel {
	DISCORD,
	INAPP,
	EMAIL
}
