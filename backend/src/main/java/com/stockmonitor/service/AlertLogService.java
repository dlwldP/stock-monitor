package com.stockmonitor.service;

import com.stockmonitor.repository.AlertLogRepository;
import com.stockmonitor.web.dto.AlertLogResponse;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AlertLogService {

	private final AlertLogRepository repository;

	public AlertLogService(AlertLogRepository repository) {
		this.repository = repository;
	}

	public List<AlertLogResponse> recent(int limit) {
		return repository.findAllByOrderByTriggeredAtDesc(PageRequest.of(0, limit)).stream()
				.map(AlertLogResponse::of)
				.toList();
	}
}
