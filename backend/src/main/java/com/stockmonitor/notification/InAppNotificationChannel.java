package com.stockmonitor.notification;

import com.stockmonitor.domain.AlertChannel;
import org.springframework.stereotype.Component;

/**
 * In-app notifications don't have an external delivery step — the AlertLog
 * row that {@link NotificationDispatcher} writes for every channel attempt *is* the
 * in-app record, read by the frontend via {@code GET /api/alert-logs}. So this
 * adapter is a no-op that always "succeeds".
 */
@Component
public class InAppNotificationChannel implements NotificationChannel {

	@Override
	public AlertChannel type() {
		return AlertChannel.INAPP;
	}

	@Override
	public void send(AlertTriggeredEvent event) {
		// no-op: persistence happens in NotificationDispatcher regardless of channel
	}
}
