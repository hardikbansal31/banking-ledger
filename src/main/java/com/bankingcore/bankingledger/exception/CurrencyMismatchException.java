package com.bankingcore.bankingledger.exception;

/** Thrown when cross-currency operation is attempted without explicit conversion. HTTP 422. */
public class CurrencyMismatchException extends BankingDomainException {
    public CurrencyMismatchException(String sourceCurrency, String targetCurrency) {
        super(("Currency mismatch: source '%s' does not match target '%s'. " +
                "Use a cross-currency transfer endpoint or provide an exchange rate.")
                .formatted(sourceCurrency, targetCurrency));
    }
}