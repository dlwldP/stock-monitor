package com.stockmonitor.notification;

import com.stockmonitor.config.NotificationProperties;
import com.stockmonitor.domain.AlertChannel;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sends a Discord embed message via an incoming webhook (see
 * docs/PLANNING.md section 6). The webhook URL comes from the
 * {@code DISCORD_WEBHOOK_URL} environment variable — left unset until the
 * user configures one.
 */
@Component
public class DiscordNotificationChannel implements NotificationChannel {

	private static final int EMBED_COLOR = 0x3B82F6;

	private final NotificationProperties properties;
	private final RestClient restClient = RestClient.create();

	public DiscordNotificationChannel(NotificationProperties properties) {
		this.properties = properties;
	}

	@Override
	public AlertChannel type() {
		return AlertChannel.DISCORD;
	}

	@Override
	public void send(AlertTriggeredEvent event) throws NotificationDeliveryException {
		String webhookUrl = properties.discordWebhookUrl();
		if (webhookUrl == null || webhookUrl.isBlank()) {
			throw new NotificationDeliveryException("DISCORD_WEBHOOK_URL이 설정되어 있지 않습니다.");
		}

		Map<String, Object> embed = Map.of(
				"title", "TossWatch 알림",
				"description", event.summary(),
				"color", EMBED_COLOR,
				"timestamp", DateTimeFormatter.ISO_INSTANT.format(event.triggeredAt()),
				"fields", List.of(
						Map.of("name", "종목", "value", event.rule().getSymbol(), "inline", true),
						Map.of("name", "현재가", "value", event.quote().price().toPlainString(), "inline", true),
						Map.of("name", "목표가", "value", event.rule().getThresholdValue().toPlainString(), "inline", true)));

		try {
			restClient.post()
					.uri(webhookUrl)
					.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
					.body(Map.of("embeds", List.of(embed)))
					.retrieve()
					.toBodilessEntity();
		} catch (RestClientException e) {
			throw new NotificationDeliveryException("Discord webhook 전송 실패: " + e.getMessage(), e);
		}
	}
}
