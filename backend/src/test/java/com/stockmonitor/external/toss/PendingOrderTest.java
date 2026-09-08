package com.stockmonitor.external.toss;

import static org.assertj.core.api.Assertions.assertThat;

import com.stockmonitor.domain.Market;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PendingOrderTest {

	private PendingOrder order(String quantity, String filledQuantity) {
		return new PendingOrder(
				"1", "005930", "삼성전자", Market.KR, OrderSide.BUY,
				new BigDecimal(quantity), filledQuantity == null ? null : new BigDecimal(filledQuantity),
				new BigDecimal("68000"), Instant.now());
	}

	@Test
	void anUntouchedOrderHasEverythingRemainingAndIsNotPartiallyFilled() {
		PendingOrder order = order("5", "0");

		assertThat(order.remainingQuantity()).isEqualByComparingTo("5");
		assertThat(order.isPartiallyFilled()).isFalse();
	}

	@Test
	void aPartiallyFilledOrderReportsWhatIsLeft() {
		PendingOrder order = order("5", "2");

		assertThat(order.remainingQuantity()).isEqualByComparingTo("3");
		assertThat(order.isPartiallyFilled()).isTrue();
	}

	@Test
	void aFullyFilledOrderIsNotReportedAsPartial() {
		// Shouldn't normally appear in a pending list, but the API deciding otherwise
		// shouldn't make the row claim to be half-done.
		PendingOrder order = order("5", "5");

		assertThat(order.remainingQuantity()).isEqualByComparingTo("0");
		assertThat(order.isPartiallyFilled()).isFalse();
	}

	@Test
	void treatsAMissingFilledQuantityAsNothingFilled() {
		PendingOrder order = order("5", null);

		assertThat(order.remainingQuantity()).isEqualByComparingTo("5");
		assertThat(order.isPartiallyFilled()).isFalse();
	}

	@Test
	void neverReportsANegativeRemainingQuantity() {
		// An over-fill would otherwise render as a negative "미체결수량" on screen.
		PendingOrder order = order("5", "7");

		assertThat(order.remainingQuantity()).isEqualByComparingTo("0");
	}
}
