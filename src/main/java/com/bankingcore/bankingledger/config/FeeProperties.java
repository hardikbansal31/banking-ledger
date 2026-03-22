package com.bankingcore.bankingledger.config;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

/**
 * FeeProperties — typed binding for app.fee.* in application.yml.
 *
 * Tier structure:
 *   amount < tierLowThreshold                → tierLowRate  (highest fee %)
 *   tierLowThreshold <= amount < tierHigh    → tierMidRate
 *   amount >= tierHighThreshold              → tierHighRate (lowest fee %)
 *
 * This is a standard volume-discount model: larger transfers pay
 * a lower percentage fee, incentivising platform usage for big transfers.
 *
 * All values are BigDecimal strings in config to avoid floating-point
 * representation issues at the configuration layer.
 */
@Component
@ConfigurationProperties(prefix = "app.fee")
@Validated
@Getter
@Setter
public class FeeProperties {

    @NotNull
    private BigDecimal tierLowThreshold;   // e.g. 1000.00

    @NotNull
    private BigDecimal tierHighThreshold;  // e.g. 10000.00

    @NotNull
    private BigDecimal tierLowRate;        // e.g. 0.0150 = 1.50%

    @NotNull
    private BigDecimal tierMidRate;        // e.g. 0.0100 = 1.00%

    @NotNull
    private BigDecimal tierHighRate;       // e.g. 0.0050 = 0.50%
}