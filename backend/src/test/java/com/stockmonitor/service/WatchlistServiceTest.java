package com.stockmonitor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stockmonitor.domain.Market;
import com.stockmonitor.domain.WatchlistItem;
import com.stockmonitor.external.toss.Quote;
import com.stockmonitor.external.toss.SymbolRef;
import com.stockmonitor.external.toss.TossApiClient;
import com.stockmonitor.repository.WatchlistItemRepository;
import com.stockmonitor.web.dto.WatchlistItemRequest;
import com.stockmonitor.web.dto.WatchlistItemResponse;
import com.stockmonitor.web.exception.ConflictException;
import com.stockmonitor.web.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

	@Mock
	private WatchlistItemRepository repository;

	@Mock
	private TossApiClient tossApiClient;

	private WatchlistService service;

	@BeforeEach
	void setUp() {
		service = new WatchlistService(repository, tossApiClient);
	}

	private WatchlistItem item(String symbol, Market market) {
		return new WatchlistItem(symbol, market, null);
	}

	private Quote quote() {
		return new Quote(
				"005930", Market.KR, new BigDecimal("70000"), new BigDecimal("1.5"), 1000, 1000,
				new BigDecimal("90000"), new BigDecimal("50000"), Instant.now());
	}

	@Test
	void listFetchesEveryItemsQuoteInOneBatchedLookup() {
		when(repository.findAll()).thenReturn(List.of(item("005930", Market.KR), item("AAPL", Market.US)));
		when(tossApiClient.getQuotes(anyCollection())).thenReturn(Map.of(
				new SymbolRef("005930", Market.KR), quote(),
				new SymbolRef("AAPL", Market.US), quote()));

		List<WatchlistItemResponse> responses = service.list();

		assertThat(responses).hasSize(2);
		assertThat(responses).allSatisfy(r -> assertThat(r.currentPrice()).isEqualByComparingTo("70000"));
		ArgumentCaptor<Collection<SymbolRef>> captor = ArgumentCaptor.captor();
		verify(tossApiClient).getQuotes(captor.capture());
		assertThat(captor.getValue())
				.containsExactly(new SymbolRef("005930", Market.KR), new SymbolRef("AAPL", Market.US));
		verify(tossApiClient, never()).getQuote(any(), any());
	}

	@Test
	void listStillReturnsAnItemWhoseQuoteCouldNotBeFetched() {
		// The user put it on the watchlist; a failed quote shouldn't make the row disappear.
		when(repository.findAll()).thenReturn(List.of(item("005930", Market.KR)));
		when(tossApiClient.getQuotes(anyCollection())).thenReturn(Map.of());

		List<WatchlistItemResponse> responses = service.list();

		assertThat(responses).hasSize(1);
		assertThat(responses.get(0).symbol()).isEqualTo("005930");
		assertThat(responses.get(0).currentPrice()).isNull();
		assertThat(responses.get(0).changeRate()).isNull();
	}

	@Test
	void addRejectsADuplicateSymbolAndMarket() {
		when(repository.existsBySymbolAndMarket("005930", Market.KR)).thenReturn(true);

		assertThatThrownBy(() -> service.add(new WatchlistItemRequest("005930", Market.KR, "삼성전자")))
				.isInstanceOf(ConflictException.class);

		verify(repository, never()).save(any());
	}

	@Test
	void addSavesAndReturnsTheItemWithALiveQuote() {
		when(repository.existsBySymbolAndMarket("005930", Market.KR)).thenReturn(false);
		WatchlistItem saved = new WatchlistItem("005930", Market.KR, "삼성전자");
		when(repository.save(any())).thenReturn(saved);
		when(tossApiClient.getQuote("005930", Market.KR)).thenReturn(quote());

		WatchlistItemResponse response = service.add(new WatchlistItemRequest("005930", Market.KR, "삼성전자"));

		assertThat(response.symbol()).isEqualTo("005930");
		assertThat(response.currentPrice()).isEqualByComparingTo("70000");
		assertThat(response.changeRate()).isEqualByComparingTo("1.5");
	}

	@Test
	void deleteThrowsWhenTheItemDoesNotExist() {
		when(repository.existsById(99L)).thenReturn(false);

		assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(NotFoundException.class);

		verify(repository, never()).deleteById(any());
	}

	@Test
	void deleteRemovesAnExistingItem() {
		when(repository.existsById(1L)).thenReturn(true);

		service.delete(1L);

		verify(repository).deleteById(1L);
	}
}
