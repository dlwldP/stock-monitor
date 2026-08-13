package com.stockmonitor.web.dto;

import com.stockmonitor.domain.Market;
import java.math.BigDecimal;

public record HoldingResponse(
		String symbol,
		Market market,
		String name,
		BigDecimal quantity,
		BigDecimal avgPrice,
		BigDecimal currentPrice,
		BigDecimal evalAmount,
		BigDecimal pnl,
		BigDecimal pnlRate) {
}
