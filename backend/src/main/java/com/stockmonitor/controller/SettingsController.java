package com.stockmonitor.controller;

import com.stockmonitor.config.DigestProperties;
import com.stockmonitor.config.NotificationProperties;
import com.stockmonitor.config.TossApiProperties;
import com.stockmonitor.web.dto.SettingsStatusResponse;
import com.stockmonitor.web.dto.SettingsStatusResponse.DigestStatus;
import com.stockmonitor.web.dto.SettingsStatusResponse.NotificationStatus;
import com.stockmonitor.web.dto.SettingsStatusResponse.TossStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Backs the "설정" screen (docs/PLANNING.md section 8, item 5). Read-only: reports
 * which env-var-backed settings are configured, never the values themselves — see
 * README's "환경변수 / 시크릿" section for how to actually set them.
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

	private final TossApiProperties tossApiProperties;
	private final NotificationProperties notificationProperties;
	private final DigestProperties digestProperties;
	private final String smtpHost;

	public SettingsController(
			TossApiProperties tossApiProperties,
			NotificationProperties notificationProperties,
			DigestProperties digestProperties,
			@Value("${spring.mail.host:}") String smtpHost) {
		this.tossApiProperties = tossApiProperties;
		this.notificationProperties = notificationProperties;
		this.digestProperties = digestProperties;
		this.smtpHost = smtpHost;
	}

	@GetMapping("/status")
	public SettingsStatusResponse status() {
		TossStatus toss = new TossStatus(
				isSet(tossApiProperties.clientId()),
				isSet(tossApiProperties.clientSecret()),
				isSet(tossApiProperties.accountSeq()),
				tossApiProperties.useRealClient());

		NotificationStatus notification = new NotificationStatus(
				isSet(notificationProperties.discordWebhookUrl()), isSet(smtpHost), isSet(notificationProperties.emailTo()));

		DigestStatus digest = new DigestStatus(digestProperties.enabled(), digestProperties.cron());

		return new SettingsStatusResponse(toss, notification, digest);
	}

	private static boolean isSet(String value) {
		return value != null && !value.isBlank();
	}
}
