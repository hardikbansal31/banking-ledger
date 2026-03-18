package com.bankingcore.bankingledger.exception;

import java.math.BigDecimal;

/** Thrown when a debit would breach the minimum allowed balance. HTTP 422. */
public class InsufficientFundsException extends BankingDomainException {
    public InsufficientFundsException(String accountNumber, BigDecimal available, BigDecimal required) {
        super(("Account '%s' has insufficient funds. Available: %s, Required: %s.")
                .formatted(accountNumber, available.toPlainString(), required.toPlainString()));
    }
}