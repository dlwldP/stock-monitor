package com.stockmonitor.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockmonitor.domain.AlertChannel;
import com.stockmonitor.domain.AlertConditionType;
import com.stockmonitor.domain.AlertRule;
import com.stockmonitor.domain.Market;
import com.stockmonitor.external.toss.Quote;
import com.stockmonitor.external.toss.SymbolRef;
import com.stockmonitor.external.toss.TossApiClient;
import com.stockmonitor.notification.AlertTriggeredEvent;
import com.stockmonitor.notification.NotificationDispatcher;
import com.stockmonitor.repository.AlertRuleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PriceAlertSchedulerTest {

	private static final SymbolRef SAMSUNG = new SymbolRef("005930", Market.KR);
	private static final SymbolRef APPLE = new SymbolRef("AAPL", Market.US);

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
		return rule(SAMSUNG, threshold, cooldownMinutes);
	}

	private AlertRule rule(SymbolRef ref, BigDecimal threshold, int cooldownMinutes) {
		return new AlertRule(
				ref.symbol(), ref.market(), AlertConditionType.PRICE_ABOVE, threshold, Set.of(AlertChannel.INAPP), cooldownMinutes);
	}

	private Quote quoteAt(SymbolRef ref, String price) {
		return new Quote(
				ref.symbol(), ref.market(), new BigDecimal(price), BigDecimal.ZERO, 1000, 1000,
				new BigDecimal("90000"), new BigDecimal("50000"), Instant.now());
	}

	@Test
	void dispatchesAndStampsLastTriggeredAtWhenConditionMet() {
		AlertRule rule = rule(new BigDecimal("70000"), 60);
		when(alertRuleRepository.findByActiveTrue()).thenReturn(List.of(rule));
		when(tossApiClient.getQuotes(anyCollection())).thenReturn(Map.of(SAMSUNG, quoteAt(SAMSUNG, "70000")));

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
		when(tossApiClient.getQuotes(anyCollection())).thenReturn(Map.of(SAMSUNG, quoteAt(SAMSUNG, "69999")));

		scheduler.evaluateAlertRules();

		verify(dispatcher, never()).dispatch(any());
		assertThat(rule.getLastTriggeredAt()).isNull();
	}

	@Test
	void doesNotDispatchAgainWithinTheCooldownWindow() {
		AlertRule rule = rule(new BigDecimal("70000"), 60);
		rule.setLastTriggeredAt(Instant.now().minusSeconds(30 * 60)); // 30 min ago, cooldown is 60 min
		when(alertRuleRepository.findByActiveTrue()).thenReturn(List.of(rule));
		when(tossApiClient.getQuotes(anyCollection())).thenReturn(Map.of(SAMSUNG, quoteAt(SAMSUNG, "70000")));

		scheduler.evaluateAlertRules();

		verify(dispatcher, never()).dispatch(any());
	}

	@Test
	void doesNotQueryQuotesWhenThereAreNoActiveRules() {
		when(alertRuleRepository.findByActiveTrue()).thenReturn(List.of());

		scheduler.evaluateAlertRules();

		verify(tossApiClient, never()).getQuotes(anyCollection());
	}

	@Test
	void asksForEachSymbolOnceEvenWithSeveralRulesOnIt() {
		// The whole point of batching: three rules on two symbols is one lookup of two
		// symbols, not three separate requests.
		when(alertRuleRepository.findByActiveTrue()).thenReturn(List.of(
				rule(SAMSUNG, new BigDecimal("70000"), 60),
				rule(SAMSUNG, new BigDecimal("80000"), 60),
				rule(APPLE, new BigDecimal("200"), 60)));
		when(tossApiClient.getQuotes(anyCollection())).thenReturn(Map.of());

		scheduler.evaluateAlertRules();

		ArgumentCaptor<Collection<SymbolRef>> captor = ArgumentCaptor.captor();
		verify(tossApiClient, times(1)).getQuotes(captor.capture());
		assertThat(captor.getValue()).containsExactlyInAnyOrder(SAMSUNG, SAMSUNG, APPLE);
	}

	@Test
	void skipsRulesWhoseSymbolIsMissingFromTheBatchWithoutAffectingTheRest() {
		AlertRule unavailable = rule(APPLE, new BigDecimal("200"), 60);
		AlertRule healthy = rule(SAMSUNG, new BigDecimal("70000"), 60);
		when(alertRuleRepository.findByActiveTrue()).thenReturn(List.of(unavailable, healthy));
		// Apple's quote couldn't be fetched, so it simply isn't in the map.
		when(tossApiClient.getQuotes(anyCollection())).thenReturn(Map.of(SAMSUNG, quoteAt(SAMSUNG, "70000")));

		scheduler.evaluateAlertRules();

		verify(dispatcher, times(1)).dispatch(any());
		assertThat(unavailable.getLastTriggeredAt()).isNull();
		assertThat(healthy.getLastTriggeredAt()).isNotNull();
	}
}
