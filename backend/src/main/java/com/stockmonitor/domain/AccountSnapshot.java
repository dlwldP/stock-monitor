package com.stockmonitor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A periodic snapshot of account totals, used to draw the "자산 추이" trend chart.
 * Corresponds to the optional {@code accounts_cache} table in docs/PLANNING.md
 * section 7 — holdings aren't stored here (only what the trend chart needs).
 */
@Entity
@Table(name = "account_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountSnapshot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Instant snapshotAt;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal totalValue;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal dailyPnl;

	@Column(nullable = false, precision = 19, scale = 2)
	private BigDecimal dailyPnlRate;

	public AccountSnapshot(Instant snapshotAt, BigDecimal totalValue, BigDecimal dailyPnl, BigDecimal dailyPnlRate) {
		this.snapshotAt = snapshotAt;
		this.totalValue = totalValue;
		this.dailyPnl = dailyPnl;
		this.dailyPnlRate = dailyPnlRate;
	}
}
