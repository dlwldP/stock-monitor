package com.stockmonitor.scheduler;

import com.stockmonitor.config.RetentionProperties;
import com.stockmonitor.service.RetentionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs {@link RetentionService#purge()} on the configured cron (default: 03:30 daily). */
@Component
public class RetentionScheduler {

	private static final Logger log = LoggerFactory.getLogger(RetentionScheduler.class);

	private final RetentionService retentionService;
	private final RetentionProperties properties;

	public RetentionScheduler(RetentionService retentionService, RetentionProperties properties) {
		this.retentionService = retentionService;
		this.properties = properties;
	}

	@Scheduled(cron = "${retention.cron:0 30 3 * * *}")
	public void run() {
		if (!properties.enabled()) {
			return;
		}
		try {
			retentionService.purge();
		} catch (Exception e) {
			// Housekeeping failing is not worth taking anything else down for.
			log.error("Failed to purge old alert logs / account snapshots", e);
		}
	}
}
