package com.stockmonitor.service;

import com.stockmonitor.external.toss.Holding;
import com.stockmonitor.external.toss.Quote;
import com.stockmonitor.external.toss.TossApiClient;
import com.stockmonitor.web.dto.DashboardResponse;
import com.stockmonitor.web.dto.HoldingResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

/** Assembles account summary + holdings for the dashboard's "보유종목" section. */
@Service
public class DashboardService {

	private final TossApiClient tossApiClient;

	public DashboardService(TossApiClient tossApiClient) {
		this.tossApiClient = tossApiClient;
	}

	public DashboardResponse getDashboard() {
		var holdings = tossApiClient.getHoldings().stream().map(this::toResponse).toList();
		return new DashboardResponse(tossApiClient.getAccountSummary(), holdings);
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
