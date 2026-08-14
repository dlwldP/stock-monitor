package com.stockmonitor.external.toss;

/**
 * Wraps the Toss Securities Open API's documented error envelope:
 * {@code {"error": {"requestId", "code", "message", "data"}}}. {@code code} is the
 * machine-readable error code (e.g. {@code invalid-token}, {@code account-not-found});
 * {@code requestId} is worth logging/surfacing for CS inquiries per the docs.
 */
public class TossApiException extends RuntimeException {

	private final int httpStatus;
	private final String code;
	private final String requestId;

	public TossApiException(int httpStatus, String code, String message, String requestId) {
		super("[%s] %s (requestId=%s, http=%d)".formatted(code, message, requestId, httpStatus));
		this.httpStatus = httpStatus;
		this.code = code;
		this.requestId = requestId;
	}

	public int httpStatus() {
		return httpStatus;
	}

	public String code() {
		return code;
	}

	public String requestId() {
		return requestId;
	}
}
