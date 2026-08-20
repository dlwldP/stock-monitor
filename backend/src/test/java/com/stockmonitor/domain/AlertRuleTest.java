package com.stockmonitor.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockmonitor.external.toss.Quote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AlertRuleTest {

	private static final Market MARKET = Market.KR;

	private AlertRule rule(AlertConditionType type, String threshold) {
		return new AlertRule("005930", MARKET, type, new BigDecimal(threshold), Set.of(AlertChannel.INAPP), 60);
	}

	private Quote quote(String price, String changeRate, long volume, long avgVolume, String week52High, String week52Low) {
		return new Quote(
				"005930", MARKET, new BigDecimal(price), new BigDecimal(changeRate), volume, avgVolume,
				new BigDecimal(week52High), new BigDecimal(week52Low), Instant.now());
	}

	@ParameterizedTest
	@CsvSource({
		"70000, 69999, false",
		"70000, 70000, true",
		"70000, 70001, true",
	})
	void priceAbove(String threshold, String price, boolean expected) {
		AlertRule rule = rule(AlertConditionType.PRICE_ABOVE, threshold);
		Quote quote = quote(price, "0", 1000, 1000, "90000", "50000");
		assertThat(rule.isSatisfiedBy(quote)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({
		"70000, 70001, false",
		"70000, 70000, true",
		"70000, 69999, true",
	})
	void priceBelow(String threshold, String price, boolean expected) {
		AlertRule rule = rule(AlertConditionType.PRICE_BELOW, threshold);
		Quote quote = quote(price, "0", 1000, 1000, "90000", "50000");
		assertThat(rule.isSatisfiedBy(quote)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({
		"5, 4.9, false",
		"5, 5.0, true",
		"5, -5.0, true",
		"5, -5.1, true",
		"5, 0, false",
	})
	void pctChangeUsesAbsoluteValue(String threshold, String changeRate, boolean expected) {
		AlertRule rule = rule(AlertConditionType.PCT_CHANGE, threshold);
		Quote quote = quote("70000", changeRate, 1000, 1000, "90000", "50000");
		assertThat(rule.isSatisfiedBy(quote)).isEqualTo(expected);
	}

	@Test
	void volumeSpikeFiresAtOrAboveMultiplier() {
		AlertRule rule = rule(AlertConditionType.VOLUME_SPIKE, "2");
		assertThat(rule.isSatisfiedBy(quote("70000", "0", 199, 100, "90000", "50000"))).isFalse();
		assertThat(rule.isSatisfiedBy(quote("70000", "0", 200, 100, "90000", "50000"))).isTrue();
		assertThat(rule.isSatisfiedBy(quote("70000", "0", 500, 100, "90000", "50000"))).isTrue();
	}

	@Test
	void volumeSpikeNeverFiresWithoutAnAverageBaseline() {
		AlertRule rule = rule(AlertConditionType.VOLUME_SPIKE, "2");
		assertThat(rule.isSatisfiedBy(quote("70000", "0", 999_999, 0, "90000", "50000"))).isFalse();
	}

	@Test
	void week52HighNearFiresWithinThresholdPercentBelowTheHigh() {
		AlertRule rule = rule(AlertConditionType.WEEK52_HIGH_NEAR, "3");
		// 3% below 100000 is 97000 - right at the edge should still fire.
		assertThat(rule.isSatisfiedBy(quote("97000", "0", 1000, 1000, "100000", "50000"))).isTrue();
		assertThat(rule.isSatisfiedBy(quote("96999", "0", 1000, 1000, "100000", "50000"))).isFalse();
		// At or above the high also counts as "near".
		assertThat(rule.isSatisfiedBy(quote("100500", "0", 1000, 1000, "100000", "50000"))).isTrue();
	}

	@Test
	void week52LowNearFiresWithinThresholdPercentAboveTheLow() {
		AlertRule rule = rule(AlertConditionType.WEEK52_LOW_NEAR, "3");
		// 3% above 50000 is 51500.
		assertThat(rule.isSatisfiedBy(quote("51500", "0", 1000, 1000, "100000", "50000"))).isTrue();
		assertThat(rule.isSatisfiedBy(quote("51501", "0", 1000, 1000, "100000", "50000"))).isFalse();
		assertThat(rule.isSatisfiedBy(quote("49000", "0", 1000, 1000, "100000", "50000"))).isTrue();
	}

	@Test
	void cooldownElapsedWhenNeverTriggered() {
		AlertRule rule = rule(AlertConditionType.PRICE_ABOVE, "70000");
		assertThat(rule.isCooldownElapsed(Instant.now())).isTrue();
	}

	@Test
	void cooldownBlocksWithinTheWindow() {
		AlertRule rule = rule(AlertConditionType.PRICE_ABOVE, "70000");
		Instant now = Instant.now();
		rule.setLastTriggeredAt(now);
		assertThat(rule.isCooldownElapsed(now.plusSeconds(30 * 60))).isFalse();
	}

	@Test
	void cooldownElapsesAfterTheWindow() {
		AlertRule rule = rule(AlertConditionType.PRICE_ABOVE, "70000");
		Instant triggeredAt = Instant.now().minusSeconds(61 * 60);
		rule.setLastTriggeredAt(triggeredAt);
		assertThat(rule.isCooldownElapsed(Instant.now())).isTrue();
	}

	@Test
	void zeroCooldownAllowsImmediateRefire() {
		AlertRule rule = new AlertRule("005930", MARKET, AlertConditionType.PRICE_ABOVE, new BigDecimal("1"), Set.of(AlertChannel.INAPP), 0);
		Instant now = Instant.now();
		rule.setLastTriggeredAt(now);
		assertThat(rule.isCooldownElapsed(now.plusMillis(1))).isTrue();
	}
}
