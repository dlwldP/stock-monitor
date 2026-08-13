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
import org.springframework.stereotype.Component;

/**
 * Fans an {@link AlertTriggeredEvent} out to every channel configured on the rule,
 * and records one {@link AlertLog} row per channel attempt regardless of outcome —
 * that's both the audit trail and the in-app notification store.
 */
@Component
public class NotificationDispatcher {

	private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

	private final Map<AlertChannel, NotificationChannel> channelsByType;
	private final AlertLogRepository alertLogRepository;

	public NotificationDispatcher(List<NotificationChannel> channels, AlertLogRepository alertLogRepository) {
		this.channelsByType = channels.stream().collect(Collectors.toMap(NotificationChannel::type, Function.identity()));
		this.alertLogRepository = alertLogRepository;
	}

	public void dispatch(AlertTriggeredEvent event) {
		for (AlertChannel channelType : event.rule().getChannels()) {
			AlertLogStatus status;
			String message;
			NotificationChannel channel = channelsByType.get(channelType);
			if (channel == null) {
				status = AlertLogStatus.FAILED;
				message = channelType + " 채널은 아직 구현되지 않았습니다.";
				log.warn("Alert rule {}: {}", event.rule().getId(), message);
			} else {
				try {
					channel.send(event);
					status = AlertLogStatus.SUCCESS;
					message = event.summary();
				} catch (NotificationDeliveryException e) {
					status = AlertLogStatus.FAILED;
					message = e.getMessage();
					log.warn("Alert rule {} failed to notify via {}: {}", event.rule().getId(), channelType, e.getMessage());
				}
			}
			alertLogRepository.save(new AlertLog(event.rule(), event.triggeredAt(), channelType, status, message));
		}
	}
}
