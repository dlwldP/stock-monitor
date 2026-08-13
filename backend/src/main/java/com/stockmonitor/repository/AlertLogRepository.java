package com.stockmonitor.repository;

import com.stockmonitor.domain.AlertLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertLogRepository extends JpaRepository<AlertLog, Long> {

	Page<AlertLog> findAllByOrderByTriggeredAtDesc(Pageable pageable);

	/** Called before deleting an AlertRule, since alert_logs.alert_rule_id is NOT NULL (no orphaning). */
	void deleteByAlertRuleId(Long alertRuleId);
}
