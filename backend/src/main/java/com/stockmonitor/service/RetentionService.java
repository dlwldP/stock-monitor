package com.stockmonitor.service;

import com.stockmonitor.config.RetentionProperties;
import com.stockmonitor.domain.AccountSnapshot;
import com.stockmonitor.repository.AccountSnapshotRepository;
import com.stockmonitor.repository.AlertLogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the two tables that grow on a timer from growing without limit.
 *
 * <p>{@code alert_logs} gains a row per channel per firing and {@code account_snapshots} one
 * per snapshot interval (96/day at the 15-minute default), and nothing ever removed either.
 *
 * <p>The two are treated differently on purpose. Alert logs are a record of notifications
 * already delivered, so old ones are simply deleted. Snapshots are the 자산 추이 chart's
 * history — deleting a year-old portfolio value would throw away the most interesting thing
 * the chart has to show — so instead of deleting them, older ones are <em>thinned</em> to the
 * last snapshot of each day. A year then costs ~365 rows instead of ~35,000, and the long-term
 * line keeps its shape.
 */
@Service
public class RetentionService {

	private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

	private final AlertLogRepository alertLogRepository;
	private final AccountSnapshotRepository snapshotRepository;
	private final RetentionProperties properties;
	private final ZoneId zoneId;

	/** Explicit, because the test-only constructor below makes the choice ambiguous otherwise. */
	@Autowired
	public RetentionService(
			AlertLogRepository alertLogRepository,
			AccountSnapshotRepository snapshotRepository,
			RetentionProperties properties) {
		this(alertLogRepository, snapshotRepository, properties, ZoneId.systemDefault());
	}

	/** Zone is injectable so tests don't depend on where they run — "one per day" needs a calendar. */
	RetentionService(
			AlertLogRepository alertLogRepository,
			AccountSnapshotRepository snapshotRepository,
			RetentionProperties properties,
			ZoneId zoneId) {
		this.alertLogRepository = alertLogRepository;
		this.snapshotRepository = snapshotRepository;
		this.properties = properties;
		this.zoneId = zoneId;
	}

	@Transactional
	public void purge() {
		purgeAlertLogs(Instant.now());
		thinSnapshots(Instant.now());
	}

	@Transactional
	public int purgeAlertLogs(Instant now) {
		if (properties.alertLogDays() <= 0) {
			return 0;
		}
		Instant cutoff = now.minus(properties.alertLogDays(), ChronoUnit.DAYS);
		int deleted = alertLogRepository.deleteByTriggeredAtBefore(cutoff);
		if (deleted > 0) {
			log.info("Deleted {} alert log(s) older than {}", deleted, cutoff);
		}
		return deleted;
	}

	/**
	 * Collapses snapshots older than the cutoff down to the last one of each day.
	 *
	 * <p>Done in Java rather than as a bulk delete with a date-truncating SQL function: the
	 * grouping is by <em>calendar day in a specific zone</em>, which is awkward to express
	 * portably across H2 and MySQL, and the row counts here (tens of thousands at worst)
	 * comfortably fit in memory for a job that runs once a day.
	 */
	@Transactional
	public int thinSnapshots(Instant now) {
		if (properties.snapshotKeepAllDays() <= 0) {
			return 0;
		}
		Instant cutoff = now.minus(properties.snapshotKeepAllDays(), ChronoUnit.DAYS);
		List<AccountSnapshot> older = snapshotRepository.findBySnapshotAtBeforeOrderBySnapshotAtAsc(cutoff);
		if (older.size() <= 1) {
			return 0;
		}

		// Ordered ascending, so the last write for a given day wins as the keeper.
		Map<LocalDate, AccountSnapshot> keepPerDay = new LinkedHashMap<>();
		for (AccountSnapshot snapshot : older) {
			keepPerDay.put(snapshot.getSnapshotAt().atZone(zoneId).toLocalDate(), snapshot);
		}

		List<AccountSnapshot> discard = new ArrayList<>();
		for (AccountSnapshot snapshot : older) {
			if (keepPerDay.get(snapshot.getSnapshotAt().atZone(zoneId).toLocalDate()) != snapshot) {
				discard.add(snapshot);
			}
		}
		if (discard.isEmpty()) {
			return 0;
		}
		snapshotRepository.deleteAllInBatch(discard);
		log.info("Thinned {} account snapshot(s) older than {} down to one per day", discard.size(), cutoff);
		return discard.size();
	}
}
