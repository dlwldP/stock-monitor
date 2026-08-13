package com.stockmonitor.web.dto;

import com.stockmonitor.domain.AlertChannel;
import com.stockmonitor.domain.AlertConditionType;
import com.stockmonitor.domain.Market;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.Set;

public record AlertRuleRequest(
		@NotBlank String symbol,
		@NotNull Market market,
		@NotNull AlertConditionType conditionType,
		@NotNull @Positive BigDecimal thresholdValue,
		@NotEmpty Set<AlertChannel> channels,
		/** Null means "use the default" (see AlertRuleService.DEFAULT_COOLDOWN_MINUTES). */
		Integer cooldownMinutes) {
}
