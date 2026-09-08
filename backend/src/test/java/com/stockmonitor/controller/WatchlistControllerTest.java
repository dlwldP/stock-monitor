package com.stockmonitor.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockmonitor.domain.Market;
import com.stockmonitor.service.WatchlistService;
import com.stockmonitor.web.dto.WatchlistItemResponse;
import com.stockmonitor.web.exception.ConflictException;
import com.stockmonitor.web.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pins the HTTP contract of the watchlist endpoints — the status codes and error mapping that
 * the service-layer tests can't see, and that the frontend depends on.
 */
@WebMvcTest(WatchlistController.class)
class WatchlistControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private WatchlistService service;

	private WatchlistItemResponse item() {
		return new WatchlistItemResponse(
				1L, "005930", Market.KR, "삼성전자", Instant.parse("2026-09-08T00:00:00Z"),
				new BigDecimal("70000"), new BigDecimal("1.5"));
	}

	@Test
	void listsWatchlistItems() throws Exception {
		when(service.list()).thenReturn(List.of(item()));

		mockMvc.perform(get("/api/watchlist"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].symbol").value("005930"))
				.andExpect(jsonPath("$[0].currentPrice").value(70000));
	}

	@Test
	void servesAnItemWhoseQuoteIsMissingWithNullPriceRatherThanFailing() throws Exception {
		when(service.list()).thenReturn(List.of(new WatchlistItemResponse(
				1L, "005930", Market.KR, null, Instant.parse("2026-09-08T00:00:00Z"), null, null)));

		mockMvc.perform(get("/api/watchlist"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].symbol").value("005930"))
				.andExpect(jsonPath("$[0].currentPrice").doesNotExist());
	}

	@Test
	void addsAnItemAndAnswers201() throws Exception {
		when(service.add(any())).thenReturn(item());

		mockMvc.perform(post("/api/watchlist")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"symbol\":\"005930\",\"market\":\"KR\",\"displayName\":\"삼성전자\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.symbol").value("005930"));
	}

	@Test
	void rejectsABlankSymbolWithoutReachingTheService() throws Exception {
		mockMvc.perform(post("/api/watchlist")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"symbol\":\"\",\"market\":\"KR\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.details.symbol").exists());

		verify(service, never()).add(any());
	}

	@Test
	void rejectsAMissingMarketWithoutReachingTheService() throws Exception {
		mockMvc.perform(post("/api/watchlist")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"symbol\":\"005930\"}"))
				.andExpect(status().isBadRequest());

		verify(service, never()).add(any());
	}

	@Test
	void mapsADuplicateTo409() throws Exception {
		when(service.add(any())).thenThrow(new ConflictException("이미 관심종목에 등록된 종목입니다: 005930"));

		mockMvc.perform(post("/api/watchlist")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"symbol\":\"005930\",\"market\":\"KR\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("이미 관심종목에 등록된 종목입니다: 005930"));
	}

	@Test
	void deletingAnExistingItemAnswers204() throws Exception {
		mockMvc.perform(delete("/api/watchlist/1")).andExpect(status().isNoContent());

		verify(service).delete(1L);
	}

	@Test
	void mapsAMissingItemTo404() throws Exception {
		doThrow(new NotFoundException("관심종목을 찾을 수 없습니다: 99")).when(service).delete(99L);

		mockMvc.perform(delete("/api/watchlist/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.message").value("관심종목을 찾을 수 없습니다: 99"));
	}
}
