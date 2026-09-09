package com.stockmonitor.web.dto;

import com.stockmonitor.domain.AlertChannel;
import com.stockmonitor.domain.AlertTriggerMode;
import java.math.BigDecimal;
import java.util.Set;

/**
 * Partial update — every field is optional and a null one is left unchanged, so toggling
 * {@code active} from the rule-list screen doesn't require resending the rest of the rule.
 *
 * <p>{@code symbol}/{@code market}/{@code conditionType} are deliberately not here: changing
 * which symbol or which condition a rule watches is close enough to "a different rule" that
 * delete-and-recreate is the clearer operation. This covers tuning a rule's parameters —
 * threshold, channels, cooldown, repeat mode — and its on/off switch.
 */
public record AlertRuleUpdateRequest(
		Boolean active,
		BigDecimal thresholdValue,
		Set<AlertChannel> channels,
		Integer cooldownMinutes,
		AlertTriggerMode triggerMode) {
}
