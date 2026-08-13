package com.stockmonitor.digest;

import com.stockmonitor.config.NotificationProperties;
import com.stockmonitor.domain.AlertLog;
import com.stockmonitor.domain.AlertLogStatus;
import com.stockmonitor.external.toss.AccountSummary;
import com.stockmonitor.external.toss.TossApiClient;
import com.stockmonitor.repository.AlertLogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds and sends the daily "오늘 요약" digest email — today's triggered alerts plus
 * the current asset summary (docs/PLANNING.md section 10, stage 3). Not per-rule, so
 * it doesn't write to {@code alert_logs} on failure; it just logs (same "not configured
 * yet" tolerance as the per-rule channels).
 */
@Service
@Transactional(readOnly = true)
public class DigestService {

	private static final Logger log = LoggerFactory.getLogger(DigestService.class);

	private final AlertLogRepository alertLogRepository;
	private final TossApiClient tossApiClient;
	private final ObjectProvider<JavaMailSender> mailSenderProvider;
	private final NotificationProperties notificationProperties;

	public DigestService(
			AlertLogRepository alertLogRepository,
			TossApiClient tossApiClient,
			ObjectProvider<JavaMailSender> mailSenderProvider,
			NotificationProperties notificationProperties) {
		this.alertLogRepository = alertLogRepository;
		this.tossApiClient = tossApiClient;
		this.mailSenderProvider = mailSenderProvider;
		this.notificationProperties = notificationProperties;
	}

	/** @return true if an email was actually sent (false if skipped, e.g. no recipient configured). */
	public boolean sendDailyDigest() {
		String to = notificationProperties.emailTo();
		if (to == null || to.isBlank()) {
			log.info("Skipping daily digest: NOTIFICATION_EMAIL_TO is not configured.");
			return false;
		}
		JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
		if (mailSender == null) {
			log.info("Skipping daily digest: SMTP (spring.mail.*) is not configured.");
			return false;
		}

		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(to);
		message.setSubject("[TossWatch] " + LocalDate.now() + " 일일 요약");
		message.setText(buildBody());
		mailSender.send(message);
		return true;
	}

	private String buildBody() {
		Instant since = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
		List<AlertLog> todayLogs = alertLogRepository.findAllByTriggeredAtAfterOrderByTriggeredAtDesc(since);
		long succeeded = todayLogs.stream().filter(l -> l.getStatus() == AlertLogStatus.SUCCESS).count();
		long failed = todayLogs.size() - succeeded;

		AccountSummary summary = tossApiClient.getAccountSummary();

		StringBuilder body = new StringBuilder();
		body.append("오늘의 자산 현황\n");
		body.append("- 총 평가금액: %s\n".formatted(summary.totalValue().toPlainString()));
		body.append("- 당일 손익: %s (%s%%)\n\n".formatted(summary.dailyPnl().toPlainString(), summary.dailyPnlRate().toPlainString()));

		body.append("오늘 발송된 알림: 총 %d건 (성공 %d / 실패 %d)\n".formatted(todayLogs.size(), succeeded, failed));
		if (todayLogs.isEmpty()) {
			body.append("- 오늘 발동한 알림 규칙이 없습니다.\n");
		} else {
			for (AlertLog logEntry : todayLogs) {
				body.append("- [%s] %s (%s): %s\n".formatted(
						logEntry.getChannel(), logEntry.getAlertRule().getSymbol(), logEntry.getStatus(), logEntry.getMessage()));
			}
		}
		return body.toString();
	}
}
