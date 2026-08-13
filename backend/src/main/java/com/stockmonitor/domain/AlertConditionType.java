package com.stockmonitor.domain;

/** Alert condition types (see docs/PLANNING.md sections 2.2 and 7). */
public enum AlertConditionType {
	/** Fires once the current price is greater than or equal to the threshold. */
	PRICE_ABOVE,
	/** Fires once the current price is less than or equal to the threshold. */
	PRICE_BELOW,
	/** Fires once |전일 대비 등락률| is greater than or equal to threshold (a percent, e.g. 5 means ±5%). */
	PCT_CHANGE,
	/** Fires once volume is at least {@code threshold}x the (mock) average volume. */
	VOLUME_SPIKE,
	/** Fires once price is within {@code threshold}% of the 52-week high. */
	WEEK52_HIGH_NEAR,
	/** Fires once price is within {@code threshold}% of the 52-week low. */
	WEEK52_LOW_NEAR
}
