package com.stockmonitor.service;

import com.stockmonitor.domain.AlertChannel;
import com.stockmonitor.domain.AlertLogStatus;
import com.stockmonitor.repository.AlertLogRepository;
import com.stockmonitor.web.dto.AlertLogResponse;
import com.stockmonitor.web.dto.PageResponse;
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

	/** Small unfiltered slice for the dashboard's "최근 알림" preview. */
	public List<AlertLogResponse> recent(int limit) {
		return repository.findAllByOrderByTriggeredAtDesc(PageRequest.of(0, limit)).stream()
				.map(AlertLogResponse::of)
				.toList();
	}

	/** Filtered + paginated for the dedicated 알림 히스토리 screen. */
	public PageResponse<AlertLogResponse> search(AlertChannel channel, AlertLogStatus status, int page, int size) {
		// Sort order is baked into the @Query itself; PageRequest here just carries page/size.
		var result = repository.search(channel, status, PageRequest.of(page, size));
		return PageResponse.of(result, AlertLogResponse::of);
	}
}
