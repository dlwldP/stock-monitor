package com.stockmonitor.service;

import com.stockmonitor.domain.WatchlistItem;
import com.stockmonitor.external.toss.Quote;
import com.stockmonitor.external.toss.TossApiClient;
import com.stockmonitor.repository.WatchlistItemRepository;
import com.stockmonitor.web.dto.WatchlistItemRequest;
import com.stockmonitor.web.dto.WatchlistItemResponse;
import com.stockmonitor.web.exception.ConflictException;
import com.stockmonitor.web.exception.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WatchlistService {

	private final WatchlistItemRepository repository;
	private final TossApiClient tossApiClient;

	public WatchlistService(WatchlistItemRepository repository, TossApiClient tossApiClient) {
		this.repository = repository;
		this.tossApiClient = tossApiClient;
	}

	public List<WatchlistItemResponse> list() {
		return repository.findAll().stream()
				.map(item -> {
					Quote quote = tossApiClient.getQuote(item.getSymbol(), item.getMarket());
					return WatchlistItemResponse.of(item, quote);
				})
				.toList();
	}

	@Transactional
	public WatchlistItemResponse add(WatchlistItemRequest request) {
		if (repository.existsBySymbolAndMarket(request.symbol(), request.market())) {
			throw new ConflictException("이미 관심종목에 등록된 종목입니다: " + request.symbol());
		}
		WatchlistItem saved = repository.save(new WatchlistItem(request.symbol(), request.market(), request.displayName()));
		Quote quote = tossApiClient.getQuote(saved.getSymbol(), saved.getMarket());
		return WatchlistItemResponse.of(saved, quote);
	}

	@Transactional
	public void delete(Long id) {
		if (!repository.existsById(id)) {
			throw new NotFoundException("관심종목을 찾을 수 없습니다: " + id);
		}
		repository.deleteById(id);
	}
}
