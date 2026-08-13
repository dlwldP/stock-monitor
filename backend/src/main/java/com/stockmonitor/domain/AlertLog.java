package com.stockmonitor.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One notification-send attempt for one channel. Corresponds to {@code alert_logs}
 * in docs/PLANNING.md section 7. Also doubles as the in-app notification's storage
 * (see {@link com.stockmonitor.notification.InAppNotificationChannel}).
 */
@Entity
@Table(name = "alert_logs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "alert_rule_id", nullable = false)
	private AlertRule alertRule;

	@Column(nullable = false)
	private Instant triggeredAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AlertChannel channel;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private AlertLogStatus status;

	@Lob
	private String message;

	public AlertLog(AlertRule alertRule, Instant triggeredAt, AlertChannel channel, AlertLogStatus status, String message) {
		this.alertRule = alertRule;
		this.triggeredAt = triggeredAt;
		this.channel = channel;
		this.status = status;
		this.message = message;
	}
}
