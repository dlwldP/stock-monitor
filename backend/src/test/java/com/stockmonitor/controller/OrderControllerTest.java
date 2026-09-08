package com.stockmonitor.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockmonitor.domain.Market;
import com.stockmonitor.external.toss.OrderSide;
import com.stockmonitor.external.toss.PendingOrder;
import com.stockmonitor.external.toss.TossApiClient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private TossApiClient tossApiClient;

	@Test
	void exposesTheDerivedRemainingQuantityAndPartialFlag() throws Exception {
		// The frontend renders these directly, so they're part of the contract rather than
		// something each caller recomputes.
		when(tossApiClient.getPendingOrders()).thenReturn(List.of(new PendingOrder(
				"1", "AAPL", "Apple Inc.", Market.US, OrderSide.SELL,
				new BigDecimal("3"), new BigDecimal("1"), new BigDecimal("235.00"),
				Instant.parse("2026-09-08T00:00:00Z"))));

		mockMvc.perform(get("/api/orders/pending"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].side").value("SELL"))
				.andExpect(jsonPath("$[0].quantity").value(3))
				.andExpect(jsonPath("$[0].remainingQuantity").value(2))
				.andExpect(jsonPath("$[0].partiallyFilled").value(true));
	}

	@Test
	void answersAnEmptyListWhenNothingIsPending() throws Exception {
		when(tossApiClient.getPendingOrders()).thenReturn(List.of());

		mockMvc.perform(get("/api/orders/pending"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isArray())
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void surfacesAnUpstreamFailureRatherThanPretendingThereAreNoOrders() throws Exception {
		// This endpoint hits the one Toss API this project has never verified. Swallowing a
		// failure into an empty 200 would read as "주문 없음" on screen, which is a different
		// and misleading claim, so the failure must not be caught here — it propagates (a 500
		// on a real server) and the frontend renders it on that card alone.
		when(tossApiClient.getPendingOrders())
				.thenThrow(new IllegalStateException("미체결 주문 응답에서 배열 필드를 찾지 못했습니다. result의 필드: [foo]"));

		assertThatThrownBy(() -> mockMvc.perform(get("/api/orders/pending")))
				.hasRootCauseInstanceOf(IllegalStateException.class)
				.rootCause()
				// The message names what actually came back, which is what makes it fixable.
				.hasMessageContaining("result의 필드: [foo]");
	}
}
