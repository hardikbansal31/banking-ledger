package com.bankingcore.bankingledger.exception;

/** Thrown when a requested domain resource does not exist. HTTP 404. */
public class ResourceNotFoundException extends BankingDomainException {
    public ResourceNotFoundException(String resourceType, Object id) {
        super("%s with identifier '%s' was not found.".formatted(resourceType, id));
    }
}