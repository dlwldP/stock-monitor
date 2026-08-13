package com.stockmonitor.repository;

import com.stockmonitor.domain.AlertChannel;
import com.stockmonitor.domain.AlertLog;
import com.stockmonitor.domain.AlertLogStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertLogRepository extends JpaRepository<AlertLog, Long> {

	Page<AlertLog> findAllByOrderByTriggeredAtDesc(Pageable pageable);

	/** Either filter may be null, meaning "any" — backs the 알림 히스토리 screen's channel/status filters. */
	@Query("""
			SELECT a FROM AlertLog a
			WHERE (:channel IS NULL OR a.channel = :channel)
			AND (:status IS NULL OR a.status = :status)
			ORDER BY a.triggeredAt DESC
			""")
	Page<AlertLog> search(@Param("channel") AlertChannel channel, @Param("status") AlertLogStatus status, Pageable pageable);

	/** Called before deleting an AlertRule, since alert_logs.alert_rule_id is NOT NULL (no orphaning). */
	void deleteByAlertRuleId(Long alertRuleId);
}
