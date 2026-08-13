package com.stockmonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TossWatch backend entry point.
 *
 * <p>{@code @EnableScheduling} turns on Spring's {@code @Scheduled} support, which the
 * price-polling / alert-evaluation worker (added in a later step) relies on.
 */
@EnableScheduling
@SpringBootApplication
public class StockMonitorApplication {

	public static void main(String[] args) {
		SpringApplication.run(StockMonitorApplication.class, args);
	}
}
