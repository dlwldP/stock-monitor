package com.stockmonitor.web.dto;

import com.stockmonitor.domain.AccountSnapshot;
import java.math.BigDecimal;
import java.time.Instant;

public record AccountSnapshotResponse(Instant snapshotAt, BigDecimal totalValue, BigDecimal dailyPnl, BigDecimal dailyPnlRate) {

	public static AccountSnapshotResponse of(AccountSnapshot s) {
		return new AccountSnapshotResponse(s.getSnapshotAt(), s.getTotalValue(), s.getDailyPnl(), s.getDailyPnlRate());
	}
}
