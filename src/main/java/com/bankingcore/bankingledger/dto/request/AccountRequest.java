package com.bankingcore.bankingledger.dto.request;

import com.bankingcore.bankingledger.domain.enums.AccountType;
import jakarta.validation.constraints.*;
import lombok.Data;

/** DTOs for account management endpoints. */
public class AccountRequest {

    /** POST /accounts — open a new account */
    @Data
    public static class Create {

        @NotNull(message = "Account type is required")
        private AccountType accountType;

        @NotBlank(message = "Currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO 4217 code (e.g. USD, EUR, INR)")
        private String currency;

        @DecimalMin(value = "0.00", message = "Initial deposit cannot be negative")
        @Digits(integer = 15, fraction = 2, message = "Invalid monetary amount")
        private java.math.BigDecimal initialDeposit;
    }
}