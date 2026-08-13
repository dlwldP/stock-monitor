package com.stockmonitor.controller;

import com.stockmonitor.digest.DigestService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dev/test convenience: fire the daily digest immediately instead of waiting for its cron. */
@RestController
@RequestMapping("/api/digest")
public class DigestController {

	private final DigestService digestService;

	public DigestController(DigestService digestService) {
		this.digestService = digestService;
	}

	@PostMapping("/send-now")
	public Map<String, Boolean> sendNow() {
		return Map.of("sent", digestService.sendDailyDigest());
	}
}
