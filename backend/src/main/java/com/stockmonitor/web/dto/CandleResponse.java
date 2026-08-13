package com.stockmonitor.web.dto;

import com.stockmonitor.external.toss.Candle;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CandleResponse(LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, long volume) {

	public static CandleResponse of(Candle c) {
		return new CandleResponse(c.date(), c.open(), c.high(), c.low(), c.close(), c.volume());
	}
}
