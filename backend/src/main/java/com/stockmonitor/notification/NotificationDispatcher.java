package com.stockmonitor.notification;

import com.stockmonitor.domain.AlertChannel;
import com.stockmonitor.domain.AlertLog;
import com.stockmonitor.domain.AlertLogStatus;
import com.stockmonitor.repository.AlertLogRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Fans an {@link AlertTriggeredEvent} out to every channel configured on the rule,
 * and records one {@link AlertLog} row per channel attempt regardless of outcome —
 * that's both the audit trail and the in-app notification store.
 */
@Component
public class NotificationDispatcher {

	private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

	/** One send, one retry — see {@link NotificationDeliveryException#isRetryable()} for which failures get it. */
	private static final int MAX_ATTEMPTS = 2;
	private static final long DEFAULT_RETRY_DELAY_MS = 2_000;

	private final Map<AlertChannel, NotificationChannel> channelsByType;
	private final AlertLogRepository alertLogRepository;
	private final long retryDelayMs;

	/** Explicit, because the test-only constructor below makes the choice ambiguous otherwise. */
	@Autowired
	public NotificationDispatcher(List<NotificationChannel> channels, AlertLogRepository alertLogRepository) {
		this(channels, alertLogRepository, DEFAULT_RETRY_DELAY_MS);
	}

	/** Delay is injectable so tests exercising the retry path don't have to actually wait on it. */
	NotificationDispatcher(List<NotificationChannel> channels, AlertLogRepository alertLogRepository, long retryDelayMs) {
		this.channelsByType = channels.stream().collect(Collectors.toMap(NotificationChannel::type, Function.identity()));
		this.alertLogRepository = alertLogRepository;
		this.retryDelayMs = retryDelayMs;
	}

	public void dispatch(AlertTriggeredEvent event) {
		for (AlertChannel channelType : event.rule().getChannels()) {
			NotificationChannel channel = channelsByType.get(channelType);
			AlertLogStatus status;
			String message;
			if (channel == null) {
				status = AlertLogStatus.FAILED;
				message = channelType + " 채널은 아직 구현되지 않았습니다.";
				log.warn("Alert rule {}: {}", event.rule().getId(), message);
			} else {
				Attempt attempt = sendWithRetry(channel, channelType, event);
				status = attempt.status();
				message = attempt.message();
			}
			alertLogRepository.save(new AlertLog(event.rule(), event.triggeredAt(), channelType, status, message));
		}
	}

	/**
	 * Sends once, and retries exactly once more if the failure looks transient. A config-missing
	 * failure (no webhook URL, no SMTP host) fails the exact same way every time, so retrying it
	 * only delays a log entry that was never going to change — {@code isRetryable()} skips
	 * straight to giving up in that case.
	 */
	private Attempt sendWithRetry(NotificationChannel channel, AlertChannel channelType, AlertTriggeredEvent event) {
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				channel.send(event);
				return new Attempt(AlertLogStatus.SUCCESS, event.summary());
			} catch (NotificationDeliveryException e) {
				boolean willRetry = e.isRetryable() && attempt < MAX_ATTEMPTS;
				log.warn("Alert rule {} failed to notify via {} (attempt {}/{}{}): {}",
						event.rule().getId(), channelType, attempt, MAX_ATTEMPTS, willRetry ? ", retrying" : "", e.getMessage());
				if (!willRetry) {
					return new Attempt(AlertLogStatus.FAILED, e.getMessage());
				}
				sleep();
			}
		}
		// Unreachable with MAX_ATTEMPTS >= 1, but the compiler can't see that from the loop above.
		throw new IllegalStateException("sendWithRetry fell through without returning");
	}

	private void sleep() {
		try {
			Thread.sleep(retryDelayMs);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private record Attempt(AlertLogStatus status, String message) {
	}
}
