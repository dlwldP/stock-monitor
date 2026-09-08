package com.stockmonitor.external.toss;

import com.stockmonitor.domain.Market;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * An order that has been placed but isn't fully filled yet — the state between "주문했다" and
 * "보유종목에 반영됐다", which {@link Holding} alone can't show.
 *
 * @param quantity       ordered quantity
 * @param filledQuantity how much of it has been filled so far; {@code 0} for an untouched order
 *                       and less than {@code quantity} for a partial fill
 * @param price          limit price; may be null for a market order
 */
public record PendingOrder(
		String orderId,
		String symbol,
		String name,
		Market market,
		OrderSide side,
		BigDecimal quantity,
		BigDecimal filledQuantity,
		BigDecimal price,
		Instant orderedAt) {

	/** How much is still waiting to fill. */
	public BigDecimal remainingQuantity() {
		BigDecimal filled = filledQuantity == null ? BigDecimal.ZERO : filledQuantity;
		return quantity.subtract(filled).max(BigDecimal.ZERO);
	}

	/** Whether some of the order has filled but not all of it. */
	public boolean isPartiallyFilled() {
		return filledQuantity != null && filledQuantity.signum() > 0 && remainingQuantity().signum() > 0;
	}
}
