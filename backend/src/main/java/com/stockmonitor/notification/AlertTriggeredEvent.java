package com.stockmonitor.notification;

import com.stockmonitor.domain.AlertRule;
import com.stockmonitor.external.toss.Quote;
import java.time.Instant;

/** Carries everything a {@link NotificationChannel} needs to render and send a message. */
public record AlertTriggeredEvent(AlertRule rule, Quote quote, Instant triggeredAt) {

	public String summary() {
		String unit = rule.getMarket() == com.stockmonitor.domain.Market.KR ? "원" : "달러";
		String threshold = rule.getThresholdValue().toPlainString();
		String price = quote.price().toPlainString();
		String condition = switch (rule.getConditionType()) {
			case PRICE_ABOVE -> "목표가 %s%s 이상 도달 (현재가 %s%s)".formatted(threshold, unit, price, unit);
			case PRICE_BELOW -> "목표가 %s%s 이하 도달 (현재가 %s%s)".formatted(threshold, unit, price, unit);
			case PCT_CHANGE -> "등락률 ±%s%% 이상 (현재 %s%%, 현재가 %s%s)"
					.formatted(threshold, quote.changeRate().toPlainString(), price, unit);
			case VOLUME_SPIKE -> "거래량 평균 대비 %s배 이상 급증 (거래량 %d, 평균 %d)"
					.formatted(threshold, quote.volume(), quote.avgVolume());
			case WEEK52_HIGH_NEAR -> "52주 신고가(%s%s) %s%% 이내 근접 (현재가 %s%s)"
					.formatted(quote.week52High().toPlainString(), unit, threshold, price, unit);
			case WEEK52_LOW_NEAR -> "52주 신저가(%s%s) %s%% 이내 근접 (현재가 %s%s)"
					.formatted(quote.week52Low().toPlainString(), unit, threshold, price, unit);
		};
		return "%s %s".formatted(rule.getSymbol(), condition);
	}
}
