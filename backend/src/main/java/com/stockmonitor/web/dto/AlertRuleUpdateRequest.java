package com.stockmonitor.web.dto;

import jakarta.validation.constraints.NotNull;

/** Partial update — MVP only supports flipping the on/off switch from the rule-list UI. */
public record AlertRuleUpdateRequest(@NotNull Boolean active) {
}
