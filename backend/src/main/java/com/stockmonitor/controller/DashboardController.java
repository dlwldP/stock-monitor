package com.stockmonitor.controller;

import com.stockmonitor.service.DashboardService;
import com.stockmonitor.web.dto.AccountSnapshotResponse;
import com.stockmonitor.web.dto.DashboardResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

	private final DashboardService service;

	public DashboardController(DashboardService service) {
		this.service = service;
	}

	@GetMapping
	public DashboardResponse dashboard() {
		return service.getDashboard();
	}

	/** Oldest-first snapshots for the "자산 추이" trend chart. */
	@GetMapping("/history")
	public List<AccountSnapshotResponse> history(@RequestParam(defaultValue = "90") int limit) {
		return service.getHistory(limit);
	}
}
