package com.bankingcore.bankingledger.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

public class TransactionRequest {

    /** POST /transactions/transfer */
    @Data
    public static class Transfer {

        @NotBlank(message = "Source account number is required")
        private String sourceAccountNumber;

        @NotBlank(message = "Destination account number is required")
        private String destinationAccountNumber;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 15, fraction = 4, message = "Invalid monetary amount")
        private BigDecimal amount;

        @NotBlank(message = "Currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO 4217 code")
        private String currency;

        @Size(max = 255, message = "Description must not exceed 255 characters")
        private String description;

        /**
         * Client-generated UUID to prevent duplicate transactions on retry.
         * If not provided, the service generates one — but idempotency is
         * only guaranteed when the client supplies a consistent key on retries.
         */
        private String idempotencyKey;
    }

    /** POST /transactions/deposit (ADMIN or internal use) */
    @Data
    public static class Deposit {

        @NotBlank(message = "Account number is required")
        private String accountNumber;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 15, fraction = 4)
        private BigDecimal amount;

        @NotBlank(message = "Currency is required")
        @Pattern(regexp = "^[A-Z]{3}$")
        private String currency;

        @Size(max = 255)
        private String description;

        private String idempotencyKey;
    }
}