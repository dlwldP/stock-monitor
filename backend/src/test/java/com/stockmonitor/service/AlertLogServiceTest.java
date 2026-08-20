package com.stockmonitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockmonitor.domain.AlertChannel;
import com.stockmonitor.domain.AlertConditionType;
import com.stockmonitor.domain.AlertLog;
import com.stockmonitor.domain.AlertLogStatus;
import com.stockmonitor.domain.AlertRule;
import com.stockmonitor.domain.Market;
import com.stockmonitor.repository.AlertLogRepository;
import com.stockmonitor.web.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertLogServiceTest {

	@Mock
	private AlertLogRepository repository;

	private AlertLogService service;

	@BeforeEach
	void setUp() {
		service = new AlertLogService(repository);
	}

	private AlertLog log() {
		AlertRule rule = new AlertRule("005930", Market.KR, AlertConditionType.PRICE_ABOVE, new BigDecimal("70000"), Set.of(AlertChannel.INAPP), 60);
		return new AlertLog(rule, Instant.now(), AlertChannel.INAPP, AlertLogStatus.SUCCESS, "테스트");
	}

	@Test
	void markReadFlipsAnExistingLog() {
		AlertLog log = log();
		when(repository.findById(1L)).thenReturn(Optional.of(log));

		var response = service.markRead(1L);

		assertThat(response.read()).isTrue();
		assertThat(log.isRead()).isTrue();
	}

	@Test
	void markReadThrowsWhenMissing() {
		when(repository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.markRead(99L)).isInstanceOf(NotFoundException.class);
	}

	@Test
	void markAllInAppReadDelegatesToTheBulkUpdate() {
		service.markAllInAppRead();

		verify(repository).markAllReadByChannel(AlertChannel.INAPP);
	}

	@Test
	void unreadInAppCountOnlyCountsInAppChannel() {
		when(repository.countByChannelAndReadFalse(AlertChannel.INAPP)).thenReturn(3L);

		assertThat(service.unreadInAppCount()).isEqualTo(3L);
	}
}
