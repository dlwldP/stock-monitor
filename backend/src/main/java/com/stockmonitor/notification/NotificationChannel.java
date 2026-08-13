package com.stockmonitor.notification;

import com.stockmonitor.domain.AlertChannel;

/**
 * A delivery channel adapter. Add a new channel (e.g. email, stage 2) by implementing
 * this and registering it as a Spring bean — {@link NotificationDispatcher} picks up
 * every implementation automatically via {@link #type()}.
 */
public interface NotificationChannel {

	AlertChannel type();

	/** @throws NotificationDeliveryException if the send fails, so the dispatcher can log it. */
	void send(AlertTriggeredEvent event) throws NotificationDeliveryException;
}
