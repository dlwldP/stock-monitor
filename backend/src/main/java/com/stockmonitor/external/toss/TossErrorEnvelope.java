package com.stockmonitor.external.toss;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The Toss Open API's documented error response shape: {@code {"error": {...}}}. */
@JsonIgnoreProperties(ignoreUnknown = true)
record TossErrorEnvelope(ErrorBody error) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ErrorBody(String requestId, String code, String message, Object data) {
	}
}
