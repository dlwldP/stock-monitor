package com.stockmonitor.web.dto;

import com.stockmonitor.domain.Market;
import com.stockmonitor.external.toss.OrderSide;
import com.stockmonitor.external.toss.PendingOrder;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One 미체결 주문 row.
 *
 * @param remainingQuantity  derived here rather than in the frontend, so "얼마가 아직 안 걸렸나"
 *                           has exactly one definition
 * @param partiallyFilled    whether some of it has filled — the case worth calling out on screen,
 *                           since it's neither "대기" nor "완료"
 */
public record PendingOrderResponse(
		String orderId,
		String symbol,
		String name,
		Market market,
		OrderSide side,
		BigDecimal quantity,
		BigDecimal filledQuantity,
		BigDecimal remainingQuantity,
		boolean partiallyFilled,
		BigDecimal price,
		Instant orderedAt) {

	public static PendingOrderResponse of(PendingOrder order) {
		return new PendingOrderResponse(
				order.orderId(), order.symbol(), order.name(), order.market(), order.side(),
				order.quantity(), order.filledQuantity(), order.remainingQuantity(), order.isPartiallyFilled(),
				order.price(), order.orderedAt());
	}
}
