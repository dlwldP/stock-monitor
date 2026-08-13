package com.stockmonitor.external.toss;

import java.math.BigDecimal;

/** Account-level totals, as returned by {@link TossApiClient#getAccountSummary}. */
public record AccountSummary(BigDecimal totalValue, BigDecimal dailyPnl, BigDecimal dailyPnlRate) {
}
