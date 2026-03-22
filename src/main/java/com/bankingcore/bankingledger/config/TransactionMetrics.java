package com.bankingcore.bankingledger.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * TransactionMetrics — custom Micrometer counters exposed via Actuator.
 *
 * WHY MICROMETER?
 *   Micrometer is the instrumentation facade Spring Boot uses — like SLF4J
 *   is to logging, Micrometer is to metrics. You write against Micrometer's
 *   API and it exports to whatever backend is on the classpath: Prometheus,
 *   Datadog, CloudWatch, Graphite, etc. At JPM/Goldman they use Datadog or
 *   internal Prometheus stacks — this code works with both without changes.
 *
 * METRICS EXPOSED:
 *   banking.transactions.settled.total   — count of all settled transactions
 *   banking.transactions.failed.total    — count of all failed transactions
 *   banking.fees.collected.total         — running sum of fees charged (USD)
 *
 * VIEW AT RUNTIME:
 *   GET /actuator/metrics/banking.transactions.settled.total
 *   GET /actuator/metrics/banking.fees.collected.total
 *   GET /actuator/prometheus  (full Prometheus scrape endpoint)
 *
 * These counters are in-memory — they reset on restart. For persistent
 * metrics, Prometheus scrapes them on a schedule and stores in its TSDB.
 */
@Slf4j
@Getter
@Component
public class TransactionMetrics {

    private final Counter settledCounter;
    private final Counter failedCounter;
    private final Counter feesCollectedCounter;

    public TransactionMetrics(MeterRegistry registry) {
        this.settledCounter = Counter.builder("banking.transactions.settled.total")
                .description("Total number of successfully settled transactions")
                .tag("application", "banking-ledger")
                .register(registry);

        this.failedCounter = Counter.builder("banking.transactions.failed.total")
                .description("Total number of failed transactions")
                .tag("application", "banking-ledger")
                .register(registry);

        this.feesCollectedCounter = Counter.builder("banking.fees.collected.total")
                .description("Running total of fees collected in base currency units")
                .tag("application", "banking-ledger")
                .tag("currency", "USD")
                .register(registry);

        log.info("Banking metrics registered: settled, failed, fees.collected");
    }

    /** Call this every time a transaction reaches SETTLED status. */
    public void incrementSettled() {
        settledCounter.increment();
    }

    /** Call this every time a transaction reaches FAILED status. */
    public void incrementFailed() {
        failedCounter.increment();
    }

    /**
     * Call this every time a fee is charged.
     * @param feeAmount the fee in base currency units (e.g. USD)
     */
    public void recordFeeCollected(BigDecimal feeAmount) {
        if (feeAmount != null && feeAmount.compareTo(BigDecimal.ZERO) > 0) {
            feesCollectedCounter.increment(feeAmount.doubleValue());
        }
    }
}