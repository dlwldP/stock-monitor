package com.stockmonitor.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stockmonitor.domain.AlertChannel;
import com.stockmonitor.domain.AlertConditionType;
import com.stockmonitor.domain.AlertTriggerMode;
import com.stockmonitor.domain.Market;
import com.stockmonitor.service.AlertRuleService;
import com.stockmonitor.web.dto.AlertRuleRequest;
import com.stockmonitor.web.dto.AlertRuleResponse;
import com.stockmonitor.web.dto.AlertRuleUpdateRequest;
import com.stockmonitor.web.exception.NotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;

@WebMvcTest(AlertRuleController.class)
class AlertRuleControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockBean
	private AlertRuleService service;

	private AlertRuleResponse response(AlertTriggerMode mode) {
		return new AlertRuleResponse(
				1L, "005930", Market.KR, AlertConditionType.PRICE_ABOVE, new BigDecimal("70000"),
				Set.of(AlertChannel.INAPP), true, 60, mode, null, Instant.parse("2026-09-08T00:00:00Z"));
	}

	@Test
	void createsARuleAndAnswers201() throws Exception {
		when(service.create(any())).thenReturn(response(AlertTriggerMode.EDGE));

		mockMvc.perform(post("/api/alert-rules")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"symbol":"005930","market":"KR","conditionType":"PRICE_ABOVE",
								 "thresholdValue":70000,"channels":["INAPP"]}"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.symbol").value("005930"))
				.andExpect(jsonPath("$.triggerMode").value("EDGE"));
	}

	@Test
	void passesAnExplicitTriggerModeThrough() throws Exception {
		when(service.create(any())).thenReturn(response(AlertTriggerMode.REPEAT));

		mockMvc.perform(post("/api/alert-rules")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"symbol":"005930","market":"KR","conditionType":"PRICE_ABOVE",
								 "thresholdValue":70000,"channels":["INAPP"],"triggerMode":"REPEAT"}"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.triggerMode").value("REPEAT"));

		ArgumentCaptor<AlertRuleRequest> captor = ArgumentCaptor.captor();
		verify(service).create(captor.capture());
		org.assertj.core.api.Assertions.assertThat(captor.getValue().triggerMode()).isEqualTo(AlertTriggerMode.REPEAT);
	}

	@Test
	void rejectsAnEmptyChannelSetWithoutReachingTheService() throws Exception {
		mockMvc.perform(post("/api/alert-rules")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"symbol":"005930","market":"KR","conditionType":"PRICE_ABOVE",
								 "thresholdValue":70000,"channels":[]}"""))
				.andExpect(status().isBadRequest());

		verify(service, never()).create(any());
	}

	@Test
	void rejectsANonPositiveThresholdWithoutReachingTheService() throws Exception {
		mockMvc.perform(post("/api/alert-rules")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"symbol":"005930","market":"KR","conditionType":"PRICE_ABOVE",
								 "thresholdValue":0,"channels":["INAPP"]}"""))
				.andExpect(status().isBadRequest());

		verify(service, never()).create(any());
	}

	@Test
	void updatesThresholdChannelsCooldownAndTriggerMode() throws Exception {
		when(service.update(eq(1L), any())).thenReturn(response(AlertTriggerMode.REPEAT));

		mockMvc.perform(patch("/api/alert-rules/1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"thresholdValue":80000,"channels":["DISCORD"],"cooldownMinutes":30,"triggerMode":"REPEAT"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.triggerMode").value("REPEAT"));

		ArgumentCaptor<AlertRuleUpdateRequest> captor = ArgumentCaptor.captor();
		verify(service).update(eq(1L), captor.capture());
		org.assertj.core.api.Assertions.assertThat(captor.getValue().thresholdValue()).isEqualByComparingTo("80000");
		org.assertj.core.api.Assertions.assertThat(captor.getValue().channels()).containsExactly(AlertChannel.DISCORD);
		org.assertj.core.api.Assertions.assertThat(captor.getValue().cooldownMinutes()).isEqualTo(30);
	}

	@Test
	void updateWithOnlyActiveLeavesEverythingElseNullInTheRequest() throws Exception {
		when(service.update(eq(1L), any())).thenReturn(response(AlertTriggerMode.EDGE));

		mockMvc.perform(patch("/api/alert-rules/1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"active":false}"""))
				.andExpect(status().isOk());

		ArgumentCaptor<AlertRuleUpdateRequest> captor = ArgumentCaptor.captor();
		verify(service).update(eq(1L), captor.capture());
		org.assertj.core.api.Assertions.assertThat(captor.getValue().active()).isFalse();
		org.assertj.core.api.Assertions.assertThat(captor.getValue().thresholdValue()).isNull();
	}

	@Test
	void mapsAnUpdateOnAMissingRuleTo404() throws Exception {
		when(service.update(eq(99L), any())).thenThrow(new NotFoundException("알림 규칙을 찾을 수 없습니다: 99"));

		mockMvc.perform(patch("/api/alert-rules/99")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"active\":false}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void mapsAnUnsupportedChannelTo400WithTheServicesReason() throws Exception {
		when(service.create(any())).thenThrow(new IllegalArgumentException("[FOO] 채널은 아직 지원하지 않습니다."));

		mockMvc.perform(post("/api/alert-rules")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"symbol":"005930","market":"KR","conditionType":"PRICE_ABOVE",
								 "thresholdValue":70000,"channels":["INAPP"]}"""))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.message").value("[FOO] 채널은 아직 지원하지 않습니다."));
	}
}
