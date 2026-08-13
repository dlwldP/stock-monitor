package com.stockmonitor.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal liveness check used during scaffolding to confirm the backend boots and
 * the frontend can reach it (see {@link com.stockmonitor.config.CorsConfig}).
 */
@RestController
public class HealthController {

	@GetMapping("/api/health")
	public Map<String, String> health() {
		return Map.of("status", "ok");
	}
}
