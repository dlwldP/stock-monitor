package com.stockmonitor.external.toss;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import org.springframework.http.client.ClientHttpResponse;

/**
 * Reads an error response body as text, transparently gzip-decoding it when the
 * server sent {@code Content-Encoding: gzip}.
 *
 * <p>{@link org.springframework.http.client.SimpleClientHttpRequestFactory} (JDK
 * {@code HttpURLConnection} underneath) only auto-decompresses the success path
 * ({@code getInputStream()}) — the error path ({@code getErrorStream()}, which is
 * what a non-2xx {@link ClientHttpResponse#getBody()} reads from) is left raw. A
 * WAF/CDN in front of an API can gzip even its block pages, so without this the
 * first byte of a gzip body (0x1F) reaches Jackson as an "illegal control
 * character" and the real error text never surfaces.
 */
final class TossErrorBodyReader {

	private TossErrorBodyReader() {
	}

	static String readAsText(ClientHttpResponse response) throws IOException {
		byte[] raw;
		try (InputStream in = response.getBody()) {
			raw = in.readAllBytes();
		}
		String contentEncoding = response.getHeaders().getFirst("Content-Encoding");
		if (raw.length >= 2 && (isGzip(raw) || (contentEncoding != null && contentEncoding.toLowerCase().contains("gzip")))) {
			try (InputStream gunzip = new GZIPInputStream(new java.io.ByteArrayInputStream(raw))) {
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				gunzip.transferTo(out);
				raw = out.toByteArray();
			} catch (IOException e) {
				// Not actually gzip despite the header/magic bytes - fall through with the raw bytes.
			}
		}
		return new String(raw, StandardCharsets.UTF_8);
	}

	private static boolean isGzip(byte[] raw) {
		return (raw[0] & 0xFF) == 0x1F && (raw[1] & 0xFF) == 0x8B;
	}
}
