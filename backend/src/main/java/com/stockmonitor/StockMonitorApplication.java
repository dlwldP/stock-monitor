package com.stockmonitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TossWatch backend entry point.
 *
 * <p>{@code @EnableScheduling} turns on Spring's {@code @Scheduled} support, used by
 * {@link com.stockmonitor.scheduler.PriceAlertScheduler} to poll prices and evaluate
 * alert rules.
 */
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class StockMonitorApplication {

	public static void main(String[] args) {
		SpringApplication.run(StockMonitorApplication.class, args);
	}
}
