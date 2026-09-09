package com.stockmonitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.stockmonitor.domain.AlertChannel;
import com.stockmonitor.domain.AlertConditionType;
import com.stockmonitor.domain.AlertRule;
import com.stockmonitor.domain.Market;
import com.stockmonitor.repository.AlertLogRepository;
import com.stockmonitor.repository.AlertRuleRepository;
import com.stockmonitor.domain.AlertTriggerMode;
import com.stockmonitor.web.dto.AlertRuleRequest;
import com.stockmonitor.web.dto.AlertRuleResponse;
import com.stockmonitor.web.dto.AlertRuleUpdateRequest;
import com.stockmonitor.web.exception.NotFoundException;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertRuleServiceTest {

	@Mock
	private AlertRuleRepository repository;

	@Mock
	private AlertLogRepository alertLogRepository;

	private AlertRuleService service;

	@BeforeEach
	void setUp() {
		service = new AlertRuleService(repository, alertLogRepository);
	}

	private AlertRuleRequest request(Set<AlertChannel> channels, Integer cooldown) {
		return new AlertRuleRequest("005930", Market.KR, AlertConditionType.PRICE_ABOVE, new BigDecimal("70000"), channels, cooldown, null);
	}

	@Test
	void createUsesDefaultCooldownWhenNotProvided() {
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		AlertRuleResponse response = service.create(request(Set.of(AlertChannel.INAPP), null));

		assertThat(response.cooldownMinutes()).isEqualTo(AlertRuleService.DEFAULT_COOLDOWN_MINUTES);
	}

	@Test
	void createHonorsExplicitCooldown() {
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		AlertRuleResponse response = service.create(request(Set.of(AlertChannel.INAPP), 15));

		assertThat(response.cooldownMinutes()).isEqualTo(15);
	}

	@Test
	void createRejectsNegativeCooldown() {
		assertThatThrownBy(() -> service.create(request(Set.of(AlertChannel.INAPP), -1)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("cooldownMinutes");
	}

	private AlertRule existingRule() {
		return new AlertRule("005930", Market.KR, AlertConditionType.PRICE_ABOVE, new BigDecimal("70000"), Set.of(AlertChannel.INAPP), 60);
	}

	private static AlertRuleUpdateRequest update(
			Boolean active, BigDecimal thresholdValue, Set<AlertChannel> channels, Integer cooldownMinutes, AlertTriggerMode triggerMode) {
		return new AlertRuleUpdateRequest(active, thresholdValue, channels, cooldownMinutes, triggerMode);
	}

	@Test
	void updateTogglesActive() {
		when(repository.findById(1L)).thenReturn(Optional.of(existingRule()));

		AlertRuleResponse response = service.update(1L, update(false, null, null, null, null));

		assertThat(response.active()).isFalse();
	}

	@Test
	void updateThrowsWhenRuleMissing() {
		when(repository.findById(99L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.update(99L, update(true, null, null, null, null)))
				.isInstanceOf(NotFoundException.class);
	}

	@Test
	void updateChangesThresholdChannelsCooldownAndTriggerModeTogether() {
		when(repository.findById(1L)).thenReturn(Optional.of(existingRule()));

		AlertRuleResponse response = service.update(1L, update(
				null, new BigDecimal("80000"), Set.of(AlertChannel.DISCORD), 30, AlertTriggerMode.REPEAT));

		assertThat(response.thresholdValue()).isEqualByComparingTo("80000");
		assertThat(response.channels()).containsExactly(AlertChannel.DISCORD);
		assertThat(response.cooldownMinutes()).isEqualTo(30);
		assertThat(response.triggerMode()).isEqualTo(AlertTriggerMode.REPEAT);
	}

	@Test
	void updateLeavesFieldsAloneWhenTheirRequestValueIsNull() {
		AlertRule rule = existingRule();
		when(repository.findById(1L)).thenReturn(Optional.of(rule));

		AlertRuleResponse response = service.update(1L, update(null, new BigDecimal("80000"), null, null, null));

		// Only thresholdValue was in the request; everything else is exactly what it was.
		assertThat(response.channels()).containsExactly(AlertChannel.INAPP);
		assertThat(response.cooldownMinutes()).isEqualTo(60);
		assertThat(response.active()).isTrue();
	}

	@Test
	void updateRejectsAZeroOrNegativeThreshold() {
		when(repository.findById(1L)).thenReturn(Optional.of(existingRule()));

		assertThatThrownBy(() -> service.update(1L, update(null, BigDecimal.ZERO, null, null, null)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("thresholdValue");
	}

	@Test
	void updateRejectsAnEmptyChannelSet() {
		when(repository.findById(1L)).thenReturn(Optional.of(existingRule()));

		assertThatThrownBy(() -> service.update(1L, update(null, null, Set.of(), null, null)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("channels");
	}

	@Test
	void updateRejectsANegativeCooldown() {
		when(repository.findById(1L)).thenReturn(Optional.of(existingRule()));

		assertThatThrownBy(() -> service.update(1L, update(null, null, null, -1, null)))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("cooldownMinutes");
	}

	@Test
	void deleteRemovesLogsBeforeTheRuleToAvoidTheForeignKeyConstraint() {
		when(repository.existsById(1L)).thenReturn(true);

		service.delete(1L);

		verify(alertLogRepository).deleteByAlertRuleId(1L);
		verify(repository).deleteById(1L);
	}

	@Test
	void deleteThrowsWhenRuleMissingAndNeverTouchesLogs() {
		when(repository.existsById(99L)).thenReturn(false);

		assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(NotFoundException.class);

		verifyNoInteractions(alertLogRepository);
	}
}
