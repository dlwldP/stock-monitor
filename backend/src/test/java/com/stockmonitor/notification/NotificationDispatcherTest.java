package com.stockmonitor.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockmonitor.domain.AlertChannel;
import com.stockmonitor.domain.AlertConditionType;
import com.stockmonitor.domain.AlertLog;
import com.stockmonitor.domain.AlertLogStatus;
import com.stockmonitor.domain.AlertRule;
import com.stockmonitor.domain.Market;
import com.stockmonitor.external.toss.Quote;
import com.stockmonitor.repository.AlertLogRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationDispatcherTest {

	private final AlertLogRepository alertLogRepository = mock(AlertLogRepository.class);

	private AlertRule rule(Set<AlertChannel> channels) {
		return new AlertRule("005930", Market.KR, AlertConditionType.PRICE_ABOVE, new BigDecimal("70000"), channels, 60);
	}

	private AlertTriggeredEvent event(AlertRule rule) {
		Quote quote = new Quote(
				"005930", Market.KR, new BigDecimal("70500"), BigDecimal.ZERO, 1000, 1000,
				new BigDecimal("90000"), new BigDecimal("50000"), Instant.now());
		return new AlertTriggeredEvent(rule, quote, Instant.now());
	}

	@Test
	void logsSuccessWhenTheChannelSendsCleanly() {
		NotificationChannel inApp = mock(NotificationChannel.class);
		when(inApp.type()).thenReturn(AlertChannel.INAPP);
		NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(inApp), alertLogRepository);

		dispatcher.dispatch(event(rule(Set.of(AlertChannel.INAPP))));

		ArgumentCaptor<AlertLog> captor = ArgumentCaptor.forClass(AlertLog.class);
		verify(alertLogRepository).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(AlertLogStatus.SUCCESS);
		assertThat(captor.getValue().getChannel()).isEqualTo(AlertChannel.INAPP);
	}

	@Test
	void logsFailureWhenTheChannelThrows() throws NotificationDeliveryException {
		NotificationChannel discord = mock(NotificationChannel.class);
		when(discord.type()).thenReturn(AlertChannel.DISCORD);
		org.mockito.Mockito.doThrow(new NotificationDeliveryException("webhook 미설정")).when(discord).send(org.mockito.ArgumentMatchers.any());
		NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(discord), alertLogRepository);

		dispatcher.dispatch(event(rule(Set.of(AlertChannel.DISCORD))));

		ArgumentCaptor<AlertLog> captor = ArgumentCaptor.forClass(AlertLog.class);
		verify(alertLogRepository).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(AlertLogStatus.FAILED);
		assertThat(captor.getValue().getMessage()).isEqualTo("webhook 미설정");
	}

	@Test
	void retriesOnceForATransientFailureAndSucceeds() throws NotificationDeliveryException {
		NotificationChannel discord = mock(NotificationChannel.class);
		when(discord.type()).thenReturn(AlertChannel.DISCORD);
		// Wrapping a cause is what makes a failure retryable - see NotificationDeliveryException.
		org.mockito.Mockito.doThrow(new NotificationDeliveryException("일시적 오류", new RuntimeException("timeout")))
				.doNothing()
				.when(discord).send(org.mockito.ArgumentMatchers.any());
		NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(discord), alertLogRepository, 0);

		dispatcher.dispatch(event(rule(Set.of(AlertChannel.DISCORD))));

		verify(discord, times(2)).send(org.mockito.ArgumentMatchers.any());
		ArgumentCaptor<AlertLog> captor = ArgumentCaptor.forClass(AlertLog.class);
		verify(alertLogRepository, times(1)).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(AlertLogStatus.SUCCESS);
	}

	@Test
	void givesUpAfterOneRetryIfStillFailing() throws NotificationDeliveryException {
		NotificationChannel discord = mock(NotificationChannel.class);
		when(discord.type()).thenReturn(AlertChannel.DISCORD);
		org.mockito.Mockito.doThrow(new NotificationDeliveryException("계속 실패", new RuntimeException("timeout")))
				.when(discord).send(org.mockito.ArgumentMatchers.any());
		NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(discord), alertLogRepository, 0);

		dispatcher.dispatch(event(rule(Set.of(AlertChannel.DISCORD))));

		// One initial attempt + one retry, not an unbounded loop.
		verify(discord, times(2)).send(org.mockito.ArgumentMatchers.any());
		ArgumentCaptor<AlertLog> captor = ArgumentCaptor.forClass(AlertLog.class);
		verify(alertLogRepository, times(1)).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(AlertLogStatus.FAILED);
		assertThat(captor.getValue().getMessage()).isEqualTo("계속 실패");
	}

	@Test
	void doesNotRetryAConfigMissingFailureSinceItWouldFailTheSameWayAgain() throws NotificationDeliveryException {
		NotificationChannel discord = mock(NotificationChannel.class);
		when(discord.type()).thenReturn(AlertChannel.DISCORD);
		// No cause attached - not retryable, per NotificationDeliveryException.isRetryable().
		org.mockito.Mockito.doThrow(new NotificationDeliveryException("DISCORD_WEBHOOK_URL이 설정되어 있지 않습니다."))
				.when(discord).send(org.mockito.ArgumentMatchers.any());
		NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(discord), alertLogRepository, 0);

		dispatcher.dispatch(event(rule(Set.of(AlertChannel.DISCORD))));

		verify(discord, times(1)).send(org.mockito.ArgumentMatchers.any());
	}

	@Test
	void logsFailureForAChannelWithNoRegisteredAdapter() {
		// No NotificationChannel beans at all registered for EMAIL.
		NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(), alertLogRepository);

		dispatcher.dispatch(event(rule(Set.of(AlertChannel.EMAIL))));

		ArgumentCaptor<AlertLog> captor = ArgumentCaptor.forClass(AlertLog.class);
		verify(alertLogRepository).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(AlertLogStatus.FAILED);
		assertThat(captor.getValue().getChannel()).isEqualTo(AlertChannel.EMAIL);
	}

	@Test
	void writesOneLogPerConfiguredChannel() throws NotificationDeliveryException {
		NotificationChannel discord = mock(NotificationChannel.class);
		when(discord.type()).thenReturn(AlertChannel.DISCORD);
		NotificationChannel inApp = mock(NotificationChannel.class);
		when(inApp.type()).thenReturn(AlertChannel.INAPP);
		NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(discord, inApp), alertLogRepository);

		dispatcher.dispatch(event(rule(Set.of(AlertChannel.DISCORD, AlertChannel.INAPP))));

		verify(alertLogRepository, times(2)).save(org.mockito.ArgumentMatchers.any());
		verify(discord).send(org.mockito.ArgumentMatchers.any());
		verify(inApp).send(org.mockito.ArgumentMatchers.any());
	}
}
