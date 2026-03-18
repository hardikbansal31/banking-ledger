package com.bankingcore.bankingledger.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Typed binding for the app.security.jwt.* namespace in application.yml.
 *
 * Why @ConfigurationProperties instead of @Value?
 *  - Groups related values into one object — easy to inject wherever needed
 *  - @Validated runs Bean Validation at startup — the app refuses to start
 *    if JWT_SECRET is missing, rather than failing at the first token operation
 *  - Easier to unit-test — just construct the POJO, no Spring context needed
 */
@Component
@ConfigurationProperties(prefix = "app.security.jwt")
@Validated
@Getter
@Setter
public class JwtProperties {

    /**
     * Base64-encoded secret key — injected from ${JWT_SECRET} env variable.
     * Minimum 256 bits (32 bytes) for HS256 security.
     */
    @NotBlank(message = "JWT secret must not be blank — set JWT_SECRET env variable")
    private String secret;

    /**
     * Access token lifetime in milliseconds (default 1 hour).
     */
    @Min(value = 60_000, message = "JWT expiration must be at least 60 seconds")
    private long expirationMs;

    /**
     * Refresh token lifetime in milliseconds (default 24 hours).
     */
    @Min(value = 300_000, message = "JWT refresh expiration must be at least 5 minutes")
    private long refreshExpirationMs;
}