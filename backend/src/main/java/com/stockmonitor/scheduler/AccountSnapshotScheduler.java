package com.stockmonitor.scheduler;

import com.stockmonitor.domain.AccountSnapshot;
import com.stockmonitor.external.toss.AccountSummary;
import com.stockmonitor.external.toss.TossApiClient;
import com.stockmonitor.repository.AccountSnapshotRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Periodically records the account totals so the dashboard can chart "자산 추이" over
 * time (docs/PLANNING.md section 10, stage 3). Default interval is short (15 min) so
 * the trend chart has visible data points during local dev/demo — for a real deployment
 * a daily snapshot (e.g. at market close) is more meaningful; tune via
 * {@code scheduler.snapshot.fixed-delay-ms}.
 */
@Component
public class AccountSnapshotScheduler {

	private static final Logger log = LoggerFactory.getLogger(AccountSnapshotScheduler.class);

	private final TossApiClient tossApiClient;
	private final AccountSnapshotRepository repository;

	public AccountSnapshotScheduler(TossApiClient tossApiClient, AccountSnapshotRepository repository) {
		this.tossApiClient = tossApiClient;
		this.repository = repository;
	}

	@Scheduled(
			fixedDelayString = "${scheduler.snapshot.fixed-delay-ms:900000}",
			initialDelayString = "${scheduler.snapshot.initial-delay-ms:5000}")
	@Transactional
	public void takeSnapshot() {
		try {
			AccountSummary summary = tossApiClient.getAccountSummary();
			repository.save(new AccountSnapshot(Instant.now(), summary.totalValue(), summary.dailyPnl(), summary.dailyPnlRate()));
		} catch (Exception e) {
			log.error("Failed to record account snapshot", e);
		}
	}
}
