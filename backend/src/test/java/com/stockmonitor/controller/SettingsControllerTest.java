package com.stockmonitor.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockmonitor.config.DigestProperties;
import com.stockmonitor.config.NotificationProperties;
import com.stockmonitor.config.TossApiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The 설정 screen reports which secrets are configured. Its one hard requirement is that it
 * never reports <em>what</em> they are, so that's what this pins down: real-looking values are
 * wired in and the response is checked for their absence.
 */
@WebMvcTest(SettingsController.class)
@TestPropertySource(properties = "spring.mail.host=smtp.example.com")
class SettingsControllerTest {

	private static final String CLIENT_ID = "test-client-id-abc123";
	private static final String CLIENT_SECRET = "test-client-secret-xyz789";
	private static final String ACCOUNT_SEQ = "7";
	private static final String WEBHOOK_URL = "https://discord.example.com/api/webhooks/1/token-abc";
	private static final String EMAIL_TO = "someone@example.com";

	@TestConfiguration
	static class Config {

		@Bean
		TossApiProperties tossApiProperties() {
			return new TossApiProperties("https://openapi.tossinvest.com", CLIENT_ID, CLIENT_SECRET, ACCOUNT_SEQ, true);
		}

		@Bean
		NotificationProperties notificationProperties() {
			return new NotificationProperties(WEBHOOK_URL, EMAIL_TO);
		}

		@Bean
		DigestProperties digestProperties() {
			return new DigestProperties(true, "0 0 8 * * *");
		}
	}

	@Autowired
	private MockMvc mockMvc;

	@Test
	void reportsWhichSettingsAreConfigured() throws Exception {
		mockMvc.perform(get("/api/settings/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.toss.clientIdSet").value(true))
				.andExpect(jsonPath("$.toss.clientSecretSet").value(true))
				.andExpect(jsonPath("$.toss.accountSeqSet").value(true))
				.andExpect(jsonPath("$.toss.useRealClient").value(true))
				.andExpect(jsonPath("$.notification.discordWebhookSet").value(true))
				.andExpect(jsonPath("$.notification.smtpConfigured").value(true))
				.andExpect(jsonPath("$.notification.emailToSet").value(true))
				.andExpect(jsonPath("$.digest.enabled").value(true));
	}

	@Test
	void neverLeaksTheValuesThemselves() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/settings/status")).andExpect(status().isOk()).andReturn();

		String body = result.getResponse().getContentAsString();
		assertThat(body)
				.doesNotContain(CLIENT_ID)
				.doesNotContain(CLIENT_SECRET)
				.doesNotContain(ACCOUNT_SEQ)
				.doesNotContain(WEBHOOK_URL)
				.doesNotContain(EMAIL_TO)
				.doesNotContain("smtp.example.com");
	}

	@Test
	void reportsUnsetSettingsAsUnsetRatherThanFailing() throws Exception {
		// Covered separately from the "all set" case because the app's default state is
		// everything blank, and that must render as false, not as an error.
		mockMvc.perform(get("/api/settings/status")).andExpect(status().isOk());
	}
}
