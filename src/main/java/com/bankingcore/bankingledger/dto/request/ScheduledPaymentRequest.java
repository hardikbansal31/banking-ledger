package com.bankingcore.bankingledger.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

public class ScheduledPaymentRequest {

    @Data
    public static class Create {

        @NotBlank(message = "Source account number is required")
        private String sourceAccountNumber;

        @NotBlank(message = "Destination account number is required")
        private String destinationAccountNumber;

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 15, fraction = 4)
        private BigDecimal amount;

        @NotBlank(message = "Currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO 4217 code")
        private String currency;

        @Size(max = 255)
        private String description;

        /**
         * Quartz cron expression in the format: seconds minutes hours dayOfMonth month dayOfWeek
         * Examples:
         *   "0 0 9 1 * ?"    — 9am on 1st of every month
         *   "0 0 8 ? * MON"  — 8am every Monday
         *   "0 0 12 * * ?"   — noon every day
         */
        @NotBlank(message = "Cron expression is required")
        private String cronExpression;
    }
}