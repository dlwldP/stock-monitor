package com.stockmonitor.repository;

import com.stockmonitor.domain.AlertChannel;
import com.stockmonitor.domain.AlertLog;
import com.stockmonitor.domain.AlertLogStatus;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlertLogRepository extends JpaRepository<AlertLog, Long> {

	Page<AlertLog> findAllByOrderByTriggeredAtDesc(Pageable pageable);

	/** Backs the daily digest email — everything triggered since local midnight. */
	List<AlertLog> findAllByTriggeredAtAfterOrderByTriggeredAtDesc(Instant since);

	/** Unread count for the in-app notification badge (see AlertLog.read). */
	long countByChannelAndReadFalse(AlertChannel channel);

	@Modifying
	@Query("UPDATE AlertLog a SET a.read = true WHERE a.channel = :channel AND a.read = false")
	int markAllReadByChannel(@Param("channel") AlertChannel channel);

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
