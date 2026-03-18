package com.bankingcore.bankingledger;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * BankingLedgerApplication — Spring Boot entry point.
 *
 * @SpringBootApplication is a shortcut for:
 *   @Configuration       — this class can define @Bean methods
 *   @EnableAutoConfiguration — Spring Boot auto-wires based on classpath
 *   @ComponentScan       — scans com.bankingcore.bankingledger and sub-packages
 *
 * @EnableScheduling — activates @Scheduled annotations (needed for Phase 5 Quartz jobs)
 * @EnableAsync      — allows @Async methods (Phase 5 background tasks)
 * @EnableConfigurationProperties — activates @ConfigurationProperties beans
 *                                  (JwtProperties etc.)
 */
@Slf4j
@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties
public class BankingLedgerApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(BankingLedgerApplication.class);
		var context = app.run(args);

		String port = context.getEnvironment().getProperty("server.port", "8080");
		String ctx  = context.getEnvironment().getProperty("server.servlet.context-path", "");
		String env  = context.getEnvironment().getProperty("spring.profiles.active", "default");

		log.info("═══════════════════════════════════════════════════");
		log.info("  Banking Ledger started successfully");
		log.info("  Environment : {}", env);
		log.info("  Base URL    : http://localhost:{}{}", port, ctx);
		log.info("  Auth        : http://localhost:{}{}/auth/register", port, ctx);
		log.info("  Actuator    : http://localhost:{}{}/actuator/health", port, ctx);
		log.info("═══════════════════════════════════════════════════");
	}
}
