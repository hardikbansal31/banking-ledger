package com.bankingcore.bankingledger.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * ExchangeRateProperties — typed binding for app.exchange-rate.* in application.yml.
 */
@Component
@ConfigurationProperties(prefix = "app.exchange-rate")
@Validated
@Getter
@Setter
public class ExchangeRateProperties {

    @NotBlank
    private String apiKey;

    @NotBlank
    private String baseUrl;

    /** How long to cache exchange rates in Redis (minutes). Default 60. */
    @Min(1)
    private int cacheTtlMinutes = 60;
}