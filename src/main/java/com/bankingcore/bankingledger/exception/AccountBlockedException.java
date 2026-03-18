package com.bankingcore.bankingledger.exception;

/** Thrown when a blocked/frozen/closed account attempts a transaction. HTTP 403. */
public class AccountBlockedException extends BankingDomainException {
    public AccountBlockedException(String accountNumber) {
        super("Account '%s' is blocked or closed and cannot process transactions.".formatted(accountNumber));
    }
}