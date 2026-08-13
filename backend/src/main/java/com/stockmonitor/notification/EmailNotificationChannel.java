package com.stockmonitor.notification;

import com.stockmonitor.config.NotificationProperties;
import com.stockmonitor.domain.AlertChannel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends a plain-text email via SMTP (docs/PLANNING.md section 6). Configured through
 * {@code spring.mail.*} (host/port/username/password) and {@code notification.email-to}
 * — all read from environment variables, all unset until the user configures them.
 *
 * <p>{@link JavaMailSender} is looked up lazily via {@link ObjectProvider} rather than
 * injected directly: Spring Boot's mail autoconfiguration only activates when
 * {@code spring.mail.host} resolves to a non-blank value, so the bean may simply not
 * exist yet. That's treated the same as "not configured", not a startup failure.
 */
@Component
public class EmailNotificationChannel implements NotificationChannel {

	private final ObjectProvider<JavaMailSender> mailSenderProvider;
	private final NotificationProperties properties;

	public EmailNotificationChannel(ObjectProvider<JavaMailSender> mailSenderProvider, NotificationProperties properties) {
		this.mailSenderProvider = mailSenderProvider;
		this.properties = properties;
	}

	@Override
	public AlertChannel type() {
		return AlertChannel.EMAIL;
	}

	@Override
	public void send(AlertTriggeredEvent event) throws NotificationDeliveryException {
		String to = properties.emailTo();
		if (to == null || to.isBlank()) {
			throw new NotificationDeliveryException("NOTIFICATION_EMAIL_TO가 설정되어 있지 않습니다.");
		}
		JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
		if (mailSender == null) {
			throw new NotificationDeliveryException("SMTP(spring.mail.*)가 설정되어 있지 않습니다.");
		}

		SimpleMailMessage message = new SimpleMailMessage();
		message.setTo(to);
		message.setSubject("[TossWatch] " + event.rule().getSymbol() + " 알림");
		message.setText(event.summary());
		try {
			mailSender.send(message);
		} catch (MailException e) {
			throw new NotificationDeliveryException("이메일 전송 실패: " + e.getMessage(), e);
		}
	}
}
