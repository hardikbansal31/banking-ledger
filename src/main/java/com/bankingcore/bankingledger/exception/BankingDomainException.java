package com.bankingcore.bankingledger.exception;

/**
 * Root unchecked exception for all banking-domain errors.
 * Catch this type to handle any domain-level failure generically.
 */
public class BankingDomainException extends RuntimeException {
    public BankingDomainException(String message) { super(message); }
    public BankingDomainException(String message, Throwable cause) { super(message, cause); }
}