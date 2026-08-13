package com.stockmonitor.controller;

import com.stockmonitor.service.AlertLogService;
import com.stockmonitor.web.dto.AlertLogResponse;
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

	@GetMapping
	public List<AlertLogResponse> recent(@RequestParam(defaultValue = "20") int limit) {
		return service.recent(limit);
	}
}
