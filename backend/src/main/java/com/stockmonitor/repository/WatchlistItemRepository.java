package com.stockmonitor.repository;

import com.stockmonitor.domain.Market;
import com.stockmonitor.domain.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long> {

	boolean existsBySymbolAndMarket(String symbol, Market market);
}
