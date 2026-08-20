package com.stockmonitor.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import com.stockmonitor.external.toss.Quote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A price alert rule. Corresponds to {@code alert_rules} in docs/PLANNING.md section 7.
 * It targets a symbol directly rather than a {@link WatchlistItem} FK, matching the
 * original schema — a rule doesn't require the symbol to be on the watchlist.
 */
@Entity
@Table(name = "alert_rules")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertRule {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 20)
	private String symbol;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private Market market;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private AlertConditionType conditionType;

	@Column(nullable = false, precision = 19, scale = 4)
	private BigDecimal thresholdValue;

	@ElementCollection(fetch = jakarta.persistence.FetchType.EAGER)
	@CollectionTable(name = "alert_rule_channels", joinColumns = @JoinColumn(name = "alert_rule_id"))
	@Enumerated(EnumType.STRING)
	@Column(name = "channel", length = 20)
	private Set<AlertChannel> channels = new HashSet<>();

	@Column(nullable = false)
	private boolean active = true;

	/** Minimum minutes between two notifications for the same rule (see PLANNING.md section 6). */
	@Column(nullable = false)
	private int cooldownMinutes = 60;

	/** Last time this rule actually fired a notification; null if never. Drives the cooldown check. */
	private Instant lastTriggeredAt;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	public AlertRule(
			String symbol,
			Market market,
			AlertConditionType conditionType,
			BigDecimal thresholdValue,
			Set<AlertChannel> channels,
			int cooldownMinutes) {
		this.symbol = symbol;
		this.market = market;
		this.conditionType = conditionType;
		this.thresholdValue = thresholdValue;
		this.channels = new HashSet<>(channels);
		this.cooldownMinutes = cooldownMinutes;
	}

	/** Whether the given quote satisfies this rule's condition. */
	public boolean isSatisfiedBy(Quote quote) {
		return switch (conditionType) {
			case PRICE_ABOVE -> quote.price().compareTo(thresholdValue) >= 0;
			case PRICE_BELOW -> quote.price().compareTo(thresholdValue) <= 0;
			case PCT_CHANGE -> quote.changeRate().abs().compareTo(thresholdValue) >= 0;
			case VOLUME_SPIKE -> quote.avgVolume() > 0
					&& BigDecimal.valueOf(quote.volume())
							.compareTo(BigDecimal.valueOf(quote.avgVolume()).multiply(thresholdValue)) >= 0;
			case WEEK52_HIGH_NEAR -> quote.week52High().signum() > 0
					&& percentGap(quote.week52High(), quote.price()).compareTo(thresholdValue) <= 0;
			case WEEK52_LOW_NEAR -> quote.week52Low().signum() > 0
					&& percentGap(quote.week52Low(), quote.price()).compareTo(thresholdValue) <= 0;
		};
	}

	/** {@code (base - other) / base * 100}, as a non-negative percent gap. */
	private static BigDecimal percentGap(BigDecimal base, BigDecimal other) {
		return base.subtract(other).abs().divide(base, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
	}

	/** Whether enough time has passed since the last firing (or it never fired) to notify again. */
	public boolean isCooldownElapsed(Instant now) {
		return lastTriggeredAt == null || Instant.ofEpochMilli(lastTriggeredAt.toEpochMilli())
				.plusSeconds(cooldownMinutes * 60L)
				.isBefore(now);
	}
}
