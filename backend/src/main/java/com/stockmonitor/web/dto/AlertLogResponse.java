package com.stockmonitor.web.dto;

import com.stockmonitor.domain.AlertChannel;
import com.stockmonitor.domain.AlertLog;
import com.stockmonitor.domain.AlertLogStatus;
import java.time.Instant;

public record AlertLogResponse(
		Long id,
		Long alertRuleId,
		String symbol,
		Instant triggeredAt,
		AlertChannel channel,
		AlertLogStatus status,
		String message,
		boolean read) {

	public static AlertLogResponse of(AlertLog logEntry) {
		return new AlertLogResponse(
				logEntry.getId(), logEntry.getAlertRule().getId(), logEntry.getAlertRule().getSymbol(),
				logEntry.getTriggeredAt(), logEntry.getChannel(), logEntry.getStatus(), logEntry.getMessage(), logEntry.isRead());
	}
}
