package com.stockmonitor.notification;

/**
 * Thrown by a {@link NotificationChannel} when it fails to deliver a message.
 *
 * <p>Whether it's worth retrying is inferred from whether a cause is attached, rather than a
 * separate flag every channel has to remember to set correctly: every channel already throws
 * this two ways — with no cause when delivery was never attempted (a webhook URL or SMTP host
 * that isn't configured, which will fail identically every time), and wrapping the underlying
 * exception when an actual send attempt failed (a network blip, a timeout — the kind of thing
 * that might succeed on a second try). {@link #isRetryable()} reads that distinction back.
 */
public class NotificationDeliveryException extends Exception {

	public NotificationDeliveryException(String message) {
		super(message);
	}

	public NotificationDeliveryException(String message, Throwable cause) {
		super(message, cause);
	}

	/** See the class Javadoc — this is exactly "was a cause attached?". */
	public boolean isRetryable() {
		return getCause() != null;
	}
}
