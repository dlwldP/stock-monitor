package com.stockmonitor.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockmonitor.domain.AlertChannel;
import com.stockmonitor.domain.AlertConditionType;
import com.stockmonitor.domain.AlertRule;
import com.stockmonitor.domain.Market;
import com.stockmonitor.external.toss.Quote;
import com.stockmonitor.external.toss.TossApiClient;
import com.stockmonitor.notification.AlertTriggeredEvent;
import com.stockmonitor.notification.NotificationDispatcher;
import com.stockmonitor.repository.AlertRuleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceAlertSchedulerTest {

	@Mock
	private AlertRuleRepository alertRuleRepository;

	@Mock
	private TossApiClient tossApiClient;

	@Mock
	private NotificationDispatcher dispatcher;

	private PriceAlertScheduler scheduler;

	@BeforeEach
	void setUp() {
		scheduler = new PriceAlertScheduler(alertRuleRepository, tossApiClient, dispatcher);
	}

	private AlertRule rule(BigDecimal threshold, int cooldownMinutes) {
		return new AlertRule("005930", Market.KR, AlertConditionType.PRICE_ABOVE, threshold, Set.of(AlertChannel.INAPP), cooldownMinutes);
	}

	private Quote quoteAt(String price) {
		return new Quote("005930", Market.KR, new BigDecimal(price), BigDecimal.ZERO, 1000, 1000, new BigDecimal("90000"), new BigDecimal("50000"), Instant.now());
	}

	@Test
	void dispatchesAndStampsLastTriggeredAtWhenConditionMet() {
		AlertRule rule = rule(new BigDecimal("70000"), 60);
		when(alertRuleRepository.findByActiveTrue()).thenReturn(List.of(rule));
		when(tossApiClient.getQuote("005930", Market.KR)).thenReturn(quoteAt("70000"));

		scheduler.evaluateAlertRules();

		ArgumentCaptor<AlertTriggeredEvent> captor = ArgumentCaptor.forClass(AlertTriggeredEvent.class);
		verify(dispatcher).dispatch(captor.capture());
		assertThat(captor.getValue().rule()).isSameAs(rule);
		assertThat(rule.getLastTriggeredAt()).isNotNull();
	}

	@Test
	void doesNotDispatchWhenConditionNotMet() {
		AlertRule rule = rule(new BigDecimal("70000"), 60);
		when(alertRuleRepository.findByActiveTrue()).thenReturn(List.of(rule));
		when(tossApiClient.getQuote("005930", Market.KR)).thenReturn(quoteAt("69999"));

		scheduler.evaluateAlertRules();

		verify(dispatcher, never()).dispatch(any());
		assertThat(rule.getLastTriggeredAt()).isNull();
	}

	@Test
	void doesNotDispatchAgainWithinTheCooldownWindow() {
		AlertRule rule = rule(new BigDecimal("70000"), 60);
		rule.setLastTriggeredAt(Instant.now().minusSeconds(30 * 60)); // 30 min ago, cooldown is 60 min
		when(alertRuleRepository.findByActiveTrue()).thenReturn(List.of(rule));
		when(tossApiClient.getQuote("005930", Market.KR)).thenReturn(quoteAt("70000"));

		scheduler.evaluateAlertRules();

		verify(dispatcher, never()).dispatch(any());
	}

	@Test
	void doesNotQueryQuotesWhenThereAreNoActiveRules() {
		when(alertRuleRepository.findByActiveTrue()).thenReturn(List.of());

		scheduler.evaluateAlertRules();

		verify(tossApiClient, never()).getQuote(any(), any());
	}

	@Test
	void oneRuleFailingDoesNotStopTheOthersFromEvaluating() {
		AlertRule failing = rule(new BigDecimal("70000"), 60);
		AlertRule healthy = rule(new BigDecimal("70000"), 60);
		when(alertRuleRepository.findByActiveTrue()).thenReturn(List.of(failing, healthy));
		when(tossApiClient.getQuote("005930", Market.KR))
				.thenThrow(new RuntimeException("boom"))
				.thenReturn(quoteAt("70000"));

		scheduler.evaluateAlertRules();

		verify(dispatcher, times(1)).dispatch(any());
		assertThat(failing.getLastTriggeredAt()).isNull();
		assertThat(healthy.getLastTriggeredAt()).isNotNull();
	}
}
