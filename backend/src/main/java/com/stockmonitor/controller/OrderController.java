package com.stockmonitor.controller;

import com.stockmonitor.external.toss.TossApiClient;
import com.stockmonitor.web.dto.PendingOrderResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 미체결 주문 조회. Read-only on purpose — this app monitors an account, it doesn't place
 * orders, so there is deliberately no endpoint here that moves money.
 *
 * <p>Kept separate from {@code /api/dashboard} rather than folded into it because the orders
 * endpoint is the one piece of the Toss API this codebase has never verified (see
 * {@code TossHttpApiClient#getPendingOrders}). On its own path, a failure costs one card on the
 * screen instead of the whole dashboard.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

	private final TossApiClient tossApiClient;

	public OrderController(TossApiClient tossApiClient) {
		this.tossApiClient = tossApiClient;
	}

	@GetMapping("/pending")
	public List<PendingOrderResponse> pending() {
		return tossApiClient.getPendingOrders().stream().map(PendingOrderResponse::of).toList();
	}
}
