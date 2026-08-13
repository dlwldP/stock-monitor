package com.stockmonitor.notification;

/** Thrown by a {@link NotificationChannel} when it fails to deliver a message. */
public class NotificationDeliveryException extends Exception {

	public NotificationDeliveryException(String message) {
		super(message);
	}

	public NotificationDeliveryException(String message, Throwable cause) {
		super(message, cause);
	}
}
