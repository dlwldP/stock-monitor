package com.stockmonitor.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockmonitor.config.TossApiProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The diagnostics endpoints return the account's raw responses with no authentication, and
 * one of them will issue an arbitrary Toss API call with the account's credentials. They are
 * therefore opt-in, and that is exactly the property worth a test: "off by default" is the
 * kind of thing a refactor can silently undo.
 */
class TossDiagnosticsControllerTest {

	@TestConfiguration
	static class Config {

		@Bean
		TossApiProperties tossApiProperties() {
			return new TossApiProperties("https://openapi.tossinvest.com", "id", "secret", "1", false);
		}
	}

	@Nested
	@WebMvcTest(TossDiagnosticsController.class)
	@Import(Config.class)
	@TestPropertySource(properties = "toss.diagnostics.enabled=false")
	class WhenDisabled {

		@Autowired
		private MockMvc mockMvc;

		@Test
		void theEndpointsDoNotExist() throws Exception {
			mockMvc.perform(get("/api/toss/accounts")).andExpect(status().isNotFound());
			mockMvc.perform(get("/api/toss/raw/holdings")).andExpect(status().isNotFound());
			mockMvc.perform(get("/api/toss/raw?path=/api/v1/holdings")).andExpect(status().isNotFound());
		}
	}

	@Nested
	@WebMvcTest(TossDiagnosticsController.class)
	@Import(Config.class)
	@TestPropertySource(properties = "toss.diagnostics.enabled=true")
	class WhenExplicitlyEnabled {

		@Autowired
		private MockMvc mockMvc;

		@Test
		void theEndpointsAreServed() throws Exception {
			// use-real-client is false here, so this answers with the "turn it on first"
			// message rather than calling anything — the point is that it routes at all.
			mockMvc.perform(get("/api/toss/accounts")).andExpect(status().isOk());
		}
	}
}
