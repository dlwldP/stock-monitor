package com.stockmonitor.scheduler;

import com.stockmonitor.domain.AlertRule;
import com.stockmonitor.external.toss.Quote;
import com.stockmonitor.external.toss.TossApiClient;
import com.stockmonitor.notification.AlertTriggeredEvent;
import com.stockmonitor.notification.NotificationDispatcher;
import com.stockmonitor.repository.AlertRuleRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Polls the price of every active alert rule and fires notifications for the ones
 * whose condition is met and whose cooldown has elapsed (docs/PLANNING.md sections
 * 4 and 6). Toss has no realtime streaming, so polling is the only option there.
 */
@Component
public class PriceAlertScheduler {

	private static final Logger log = LoggerFactory.getLogger(PriceAlertScheduler.class);

	private final AlertRuleRepository alertRuleRepository;
	private final TossApiClient tossApiClient;
	private final NotificationDispatcher dispatcher;

	public PriceAlertScheduler(AlertRuleRepository alertRuleRepository, TossApiClient tossApiClient, NotificationDispatcher dispatcher) {
		this.alertRuleRepository = alertRuleRepository;
		this.tossApiClient = tossApiClient;
		this.dispatcher = dispatcher;
	}

	@Scheduled(
			fixedDelayString = "${scheduler.price-poll.fixed-delay-ms:60000}",
			initialDelayString = "${scheduler.price-poll.initial-delay-ms:10000}")
	@Transactional
	public void evaluateAlertRules() {
		List<AlertRule> rules = alertRuleRepository.findByActiveTrue();
		if (rules.isEmpty()) {
			return;
		}

		Instant now = Instant.now();
		for (AlertRule rule : rules) {
			try {
				Quote quote = tossApiClient.getQuote(rule.getSymbol(), rule.getMarket());
				if (rule.isSatisfiedBy(quote) && rule.isCooldownElapsed(now)) {
					dispatcher.dispatch(new AlertTriggeredEvent(rule, quote, now));
					rule.setLastTriggeredAt(now); // managed entity: flushed at transaction commit
				}
			} catch (Exception e) {
				log.error("Failed to evaluate alert rule {} ({} {})", rule.getId(), rule.getSymbol(), rule.getMarket(), e);
			}
		}
	}
}
