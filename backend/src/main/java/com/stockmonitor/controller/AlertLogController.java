package com.stockmonitor.controller;

import com.stockmonitor.domain.AlertChannel;
import com.stockmonitor.domain.AlertLogStatus;
import com.stockmonitor.service.AlertLogService;
import com.stockmonitor.web.dto.AlertLogResponse;
import com.stockmonitor.web.dto.PageResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alert-logs")
public class AlertLogController {

	private final AlertLogService service;

	public AlertLogController(AlertLogService service) {
		this.service = service;
	}

	/** Small unfiltered slice for the dashboard's "최근 알림" preview. */
	@GetMapping("/recent")
	public List<AlertLogResponse> recent(@RequestParam(defaultValue = "10") int limit) {
		return service.recent(limit);
	}

	/** Filtered + paginated, for the dedicated 알림 히스토리 screen. */
	@GetMapping
	public PageResponse<AlertLogResponse> search(
			@RequestParam(required = false) AlertChannel channel,
			@RequestParam(required = false) AlertLogStatus status,
			@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size) {
		return service.search(channel, status, page, size);
	}
}
