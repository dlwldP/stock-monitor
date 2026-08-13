package com.stockmonitor.web.dto;

import com.stockmonitor.domain.AlertChannel;
import com.stockmonitor.domain.AlertConditionType;
import com.stockmonitor.domain.AlertRule;
import com.stockmonitor.domain.Market;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

public record AlertRuleResponse(
		Long id,
		String symbol,
		Market market,
		AlertConditionType conditionType,
		BigDecimal thresholdValue,
		Set<AlertChannel> channels,
		boolean active,
		int cooldownMinutes,
		Instant lastTriggeredAt,
		Instant createdAt) {

	public static AlertRuleResponse of(AlertRule rule) {
		return new AlertRuleResponse(
				rule.getId(), rule.getSymbol(), rule.getMarket(), rule.getConditionType(), rule.getThresholdValue(),
				rule.getChannels(), rule.isActive(), rule.getCooldownMinutes(), rule.getLastTriggeredAt(), rule.getCreatedAt());
	}
}
