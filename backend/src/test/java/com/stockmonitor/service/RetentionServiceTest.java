package com.stockmonitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockmonitor.config.RetentionProperties;
import com.stockmonitor.domain.AccountSnapshot;
import com.stockmonitor.repository.AccountSnapshotRepository;
import com.stockmonitor.repository.AlertLogRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RetentionServiceTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	private static final Instant NOW = ZonedDateTime.of(2026, 9, 8, 3, 30, 0, 0, SEOUL).toInstant();

	@Mock
	private AlertLogRepository alertLogRepository;

	@Mock
	private AccountSnapshotRepository snapshotRepository;

	private RetentionService service(int alertLogDays, int snapshotKeepAllDays) {
		return new RetentionService(
				alertLogRepository, snapshotRepository,
				new RetentionProperties(true, "0 30 3 * * *", alertLogDays, snapshotKeepAllDays), SEOUL);
	}

	/** A snapshot at a given Seoul-local date and hour. */
	private AccountSnapshot snapshotAt(int month, int day, int hour) {
		return new AccountSnapshot(
				ZonedDateTime.of(2026, month, day, hour, 0, 0, 0, SEOUL).toInstant(),
				new BigDecimal("1000"), BigDecimal.ZERO, BigDecimal.ZERO);
	}

	@Test
	void deletesAlertLogsPastTheirRetentionWindow() {
		when(alertLogRepository.deleteByTriggeredAtBefore(any())).thenReturn(7);

		assertThat(service(90, 30).purgeAlertLogs(NOW)).isEqualTo(7);

		ArgumentCaptor<Instant> cutoff = ArgumentCaptor.captor();
		verify(alertLogRepository).deleteByTriggeredAtBefore(cutoff.capture());
		assertThat(cutoff.getValue()).isEqualTo(NOW.minusSeconds(90L * 24 * 3600));
	}

	@Test
	void keepsOnlyTheLastSnapshotOfEachOlderDay() {
		// Two days, three snapshots each: the 18:00 one survives, the earlier two don't.
		List<AccountSnapshot> older = List.of(
				snapshotAt(6, 1, 9), snapshotAt(6, 1, 12), snapshotAt(6, 1, 18),
				snapshotAt(6, 2, 9), snapshotAt(6, 2, 12), snapshotAt(6, 2, 18));
		when(snapshotRepository.findBySnapshotAtBeforeOrderBySnapshotAtAsc(any())).thenReturn(older);

		assertThat(service(90, 30).thinSnapshots(NOW)).isEqualTo(4);

		ArgumentCaptor<List<AccountSnapshot>> discarded = ArgumentCaptor.captor();
		verify(snapshotRepository).deleteAllInBatch(discarded.capture());
		assertThat(discarded.getValue()).containsExactly(
				older.get(0), older.get(1), older.get(3), older.get(4));
	}

	@Test
	void groupsByCalendarDayNotByFixed24HourBlocks() {
		// 23:00 and 01:00 are two hours apart but belong to different days, so both are kept.
		List<AccountSnapshot> older = List.of(snapshotAt(6, 1, 23), snapshotAt(6, 2, 1));
		when(snapshotRepository.findBySnapshotAtBeforeOrderBySnapshotAtAsc(any())).thenReturn(older);

		assertThat(service(90, 30).thinSnapshots(NOW)).isZero();
		verify(snapshotRepository, never()).deleteAllInBatch(any());
	}

	@Test
	void leavesRecentSnapshotsAloneEntirely() {
		// Everything inside the keep-all window is simply never queried for thinning.
		when(snapshotRepository.findBySnapshotAtBeforeOrderBySnapshotAtAsc(any())).thenReturn(List.of());

		assertThat(service(90, 30).thinSnapshots(NOW)).isZero();

		ArgumentCaptor<Instant> cutoff = ArgumentCaptor.captor();
		verify(snapshotRepository).findBySnapshotAtBeforeOrderBySnapshotAtAsc(cutoff.capture());
		assertThat(cutoff.getValue()).isEqualTo(NOW.minusSeconds(30L * 24 * 3600));
	}

	@Test
	void aDayThatAlreadyHasASingleSnapshotIsLeftUntouched() {
		List<AccountSnapshot> older = List.of(snapshotAt(6, 1, 9), snapshotAt(6, 2, 9), snapshotAt(6, 3, 9));
		when(snapshotRepository.findBySnapshotAtBeforeOrderBySnapshotAtAsc(any())).thenReturn(older);

		assertThat(service(90, 30).thinSnapshots(NOW)).isZero();
		verify(snapshotRepository, never()).deleteAllInBatch(any());
	}

	@Test
	void zeroOrNegativeRetentionDisablesThatCleanupRatherThanDeletingEverything() {
		// Guards the obvious footgun: "0 days" must not mean "delete all history".
		RetentionService disabled = service(0, 0);

		assertThat(disabled.purgeAlertLogs(NOW)).isZero();
		assertThat(disabled.thinSnapshots(NOW)).isZero();
		verify(alertLogRepository, never()).deleteByTriggeredAtBefore(any());
		verify(snapshotRepository, never()).findBySnapshotAtBeforeOrderBySnapshotAtAsc(any());
	}
}
