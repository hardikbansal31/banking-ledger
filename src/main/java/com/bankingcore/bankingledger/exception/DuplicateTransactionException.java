package com.bankingcore.bankingledger.exception;

/** Thrown on idempotency key collision (duplicate transaction). HTTP 409. */
public class DuplicateTransactionException extends BankingDomainException {
    public DuplicateTransactionException(String idempotencyKey) {
        super("A transaction with idempotency key '%s' has already been processed.".formatted(idempotencyKey));
    }
}