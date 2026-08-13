package com.stockmonitor.repository;

import com.stockmonitor.domain.AlertRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

	List<AlertRule> findByActiveTrue();

	List<AlertRule> findBySymbolAndMarket(String symbol, com.stockmonitor.domain.Market market);
}
