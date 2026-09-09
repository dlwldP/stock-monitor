package com.stockmonitor.service;

import com.stockmonitor.domain.AlertChannel;
import com.stockmonitor.domain.AlertRule;
import com.stockmonitor.repository.AlertLogRepository;
import com.stockmonitor.repository.AlertRuleRepository;
import com.stockmonitor.web.dto.AlertRuleRequest;
import com.stockmonitor.web.dto.AlertRuleResponse;
import com.stockmonitor.web.exception.NotFoundException;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AlertRuleService {

	public static final int DEFAULT_COOLDOWN_MINUTES = 60;

	private static final Set<AlertChannel> SUPPORTED_CHANNELS = Set.of(AlertChannel.DISCORD, AlertChannel.INAPP, AlertChannel.EMAIL);

	private final AlertRuleRepository repository;
	private final AlertLogRepository alertLogRepository;

	public AlertRuleService(AlertRuleRepository repository, AlertLogRepository alertLogRepository) {
		this.repository = repository;
		this.alertLogRepository = alertLogRepository;
	}

	public List<AlertRuleResponse> list() {
		return repository.findAll().stream().map(AlertRuleResponse::of).toList();
	}

	@Transactional
	public AlertRuleResponse create(AlertRuleRequest request) {
		Set<AlertChannel> unsupported = request.channels().stream()
				.filter(c -> !SUPPORTED_CHANNELS.contains(c))
				.collect(java.util.stream.Collectors.toSet());
		if (!unsupported.isEmpty()) {
			throw new IllegalArgumentException(unsupported + " 채널은 아직 지원하지 않습니다 (2단계 예정).");
		}

		int cooldown = request.cooldownMinutes() == null ? DEFAULT_COOLDOWN_MINUTES : request.cooldownMinutes();
		if (cooldown < 0) {
			throw new IllegalArgumentException("cooldownMinutes는 0 이상이어야 합니다.");
		}

		AlertRule rule = new AlertRule(
				request.symbol(), request.market(), request.conditionType(), request.thresholdValue(), request.channels(),
				cooldown, request.triggerMode());
		return AlertRuleResponse.of(repository.save(rule));
	}

	/**
	 * Applies whichever fields are non-null in the request, leaving the rest of the rule as-is.
	 * Symbol/market/condition type aren't editable here — see {@link com.stockmonitor.web.dto.AlertRuleUpdateRequest}.
	 */
	@Transactional
	public AlertRuleResponse update(Long id, com.stockmonitor.web.dto.AlertRuleUpdateRequest request) {
		AlertRule rule = repository.findById(id).orElseThrow(() -> new NotFoundException("알림 규칙을 찾을 수 없습니다: " + id));

		if (request.active() != null) {
			rule.setActive(request.active());
		}
		if (request.thresholdValue() != null) {
			if (request.thresholdValue().signum() <= 0) {
				throw new IllegalArgumentException("thresholdValue는 0보다 커야 합니다.");
			}
			rule.setThresholdValue(request.thresholdValue());
		}
		if (request.channels() != null) {
			if (request.channels().isEmpty()) {
				throw new IllegalArgumentException("channels는 최소 1개 이상이어야 합니다.");
			}
			Set<AlertChannel> unsupported = request.channels().stream()
					.filter(c -> !SUPPORTED_CHANNELS.contains(c))
					.collect(java.util.stream.Collectors.toSet());
			if (!unsupported.isEmpty()) {
				throw new IllegalArgumentException(unsupported + " 채널은 아직 지원하지 않습니다 (2단계 예정).");
			}
			rule.setChannels(new java.util.HashSet<>(request.channels()));
		}
		if (request.cooldownMinutes() != null) {
			if (request.cooldownMinutes() < 0) {
				throw new IllegalArgumentException("cooldownMinutes는 0 이상이어야 합니다.");
			}
			rule.setCooldownMinutes(request.cooldownMinutes());
		}
		if (request.triggerMode() != null) {
			rule.setTriggerMode(request.triggerMode());
		}
		return AlertRuleResponse.of(rule);
	}

	@Transactional
	public void delete(Long id) {
		if (!repository.existsById(id)) {
			throw new NotFoundException("알림 규칙을 찾을 수 없습니다: " + id);
		}
		// alert_logs.alert_rule_id is NOT NULL, so its history has to go with the rule.
		alertLogRepository.deleteByAlertRuleId(id);
		repository.deleteById(id);
	}
}
