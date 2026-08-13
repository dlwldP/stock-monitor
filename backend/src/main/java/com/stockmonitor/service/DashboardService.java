package com.stockmonitor.service;

import com.stockmonitor.external.toss.Holding;
import com.stockmonitor.external.toss.Quote;
import com.stockmonitor.external.toss.TossApiClient;
import com.stockmonitor.repository.AccountSnapshotRepository;
import com.stockmonitor.web.dto.AccountSnapshotResponse;
import com.stockmonitor.web.dto.DashboardResponse;
import com.stockmonitor.web.dto.HoldingResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assembles account summary + holdings for the dashboard's "보유종목" section. */
@Service
@Transactional(readOnly = true)
public class DashboardService {

	private final TossApiClient tossApiClient;
	private final AccountSnapshotRepository snapshotRepository;

	public DashboardService(TossApiClient tossApiClient, AccountSnapshotRepository snapshotRepository) {
		this.tossApiClient = tossApiClient;
		this.snapshotRepository = snapshotRepository;
	}

	public DashboardResponse getDashboard() {
		var holdings = tossApiClient.getHoldings().stream().map(this::toResponse).toList();
		return new DashboardResponse(tossApiClient.getAccountSummary(), holdings);
	}

	/** Oldest-first, for the "자산 추이" trend chart (see AccountSnapshotScheduler for how points are collected). */
	public List<AccountSnapshotResponse> getHistory(int limit) {
		var snapshots = snapshotRepository.findAllByOrderBySnapshotAtDesc(PageRequest.of(0, limit));
		return snapshots.reversed().stream().map(AccountSnapshotResponse::of).toList();
	}

	private HoldingResponse toResponse(Holding holding) {
		Quote quote = tossApiClient.getQuote(holding.symbol(), holding.market());
		BigDecimal evalAmount = quote.price().multiply(holding.quantity()).setScale(2, RoundingMode.HALF_UP);
		BigDecimal costAmount = holding.avgPrice().multiply(holding.quantity());
		BigDecimal pnl = evalAmount.subtract(costAmount).setScale(2, RoundingMode.HALF_UP);
		BigDecimal pnlRate = costAmount.signum() == 0
				? BigDecimal.ZERO
				: pnl.divide(costAmount, 6, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
		return new HoldingResponse(
				holding.symbol(), holding.market(), holding.name(), holding.quantity(), holding.avgPrice(),
				quote.price(), evalAmount, pnl, pnlRate);
	}
}
