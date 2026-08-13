package com.stockmonitor.controller;

import com.stockmonitor.domain.Market;
import com.stockmonitor.external.toss.TossApiClient;
import com.stockmonitor.web.dto.CandleResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/candles")
public class MarketDataController {

	private final TossApiClient tossApiClient;

	public MarketDataController(TossApiClient tossApiClient) {
		this.tossApiClient = tossApiClient;
	}

	@GetMapping
	public List<CandleResponse> candles(
			@RequestParam String symbol, @RequestParam Market market, @RequestParam(defaultValue = "60") int days) {
		int clamped = Math.max(1, Math.min(days, 365));
		return tossApiClient.getDailyCandles(symbol, market, clamped).stream().map(CandleResponse::of).toList();
	}
}
