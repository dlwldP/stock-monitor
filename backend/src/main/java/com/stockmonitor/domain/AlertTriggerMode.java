package com.stockmonitor.domain;

/**
 * How often a rule may re-fire while its condition stays true.
 *
 * <p>The cooldown alone can't express this. A "목표가 이상" rule whose stock simply stays above
 * the target is satisfied on every single poll, so with the cooldown as the only guard it
 * keeps notifying every cooldown window for as long as the price holds — which is usually
 * not what someone means by "알려줘".
 */
public enum AlertTriggerMode {

	/**
	 * Notify when the condition becomes true, then stay quiet until it goes false again
	 * (a rising edge). The cooldown still applies on top, so a price flapping across the
	 * threshold can't produce a burst of notifications.
	 */
	EDGE,

	/**
	 * Notify every time the cooldown elapses while the condition holds. Useful for something
	 * you want to keep being reminded about.
	 */
	REPEAT
}
