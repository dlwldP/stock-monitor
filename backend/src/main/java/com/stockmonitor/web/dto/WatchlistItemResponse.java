package com.stockmonitor.web.dto;

import com.stockmonitor.domain.Market;
import com.stockmonitor.domain.WatchlistItem;
import com.stockmonitor.external.toss.Quote;
import java.math.BigDecimal;
import java.time.Instant;

public record WatchlistItemResponse(
		Long id,
		String symbol,
		Market market,
		String displayName,
		Instant createdAt,
		BigDecimal currentPrice,
		BigDecimal changeRate) {

	public static WatchlistItemResponse of(WatchlistItem item, Quote quote) {
		return new WatchlistItemResponse(
				item.getId(), item.getSymbol(), item.getMarket(), item.getDisplayName(), item.getCreatedAt(),
				quote.price(), quote.changeRate());
	}
}
