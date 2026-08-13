package com.stockmonitor.notification;

import com.stockmonitor.domain.AlertRule;
import com.stockmonitor.external.toss.Quote;
import java.time.Instant;

/** Carries everything a {@link NotificationChannel} needs to render and send a message. */
public record AlertTriggeredEvent(AlertRule rule, Quote quote, Instant triggeredAt) {

	public String summary() {
		String direction = rule.getConditionType() == com.stockmonitor.domain.AlertConditionType.PRICE_ABOVE ? "이상" : "이하";
		String unit = rule.getMarket() == com.stockmonitor.domain.Market.KR ? "원" : "달러";
		return "%s 목표가 %s%s %s 도달 (현재가 %s%s)".formatted(
				rule.getSymbol(), rule.getThresholdValue().toPlainString(), unit, direction,
				quote.price().toPlainString(), unit);
	}
}
