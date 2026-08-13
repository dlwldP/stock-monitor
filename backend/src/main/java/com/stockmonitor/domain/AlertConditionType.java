package com.stockmonitor.domain;

/**
 * Alert condition types. MVP (1단계) only implements the target-price conditions;
 * pct-change / volume-spike / 52w-high-low are stage 2 (see docs/PLANNING.md section 10).
 */
public enum AlertConditionType {
	/** Fires once the current price is greater than or equal to the threshold. */
	PRICE_ABOVE,
	/** Fires once the current price is less than or equal to the threshold. */
	PRICE_BELOW
}
