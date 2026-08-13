package com.stockmonitor.digest;

import com.stockmonitor.config.DigestProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Fires {@link DigestService#sendDailyDigest()} on the configured cron (default: 08:00 daily). */
@Component
public class DigestScheduler {

	private static final Logger log = LoggerFactory.getLogger(DigestScheduler.class);

	private final DigestService digestService;
	private final DigestProperties properties;

	public DigestScheduler(DigestService digestService, DigestProperties properties) {
		this.digestService = digestService;
		this.properties = properties;
	}

	@Scheduled(cron = "${digest.cron:0 0 8 * * *}")
	public void run() {
		if (!properties.enabled()) {
			return;
		}
		try {
			digestService.sendDailyDigest();
		} catch (Exception e) {
			log.error("Failed to send daily digest", e);
		}
	}
}
