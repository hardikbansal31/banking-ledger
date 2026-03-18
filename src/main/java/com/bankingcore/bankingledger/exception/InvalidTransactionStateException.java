package com.bankingcore.bankingledger.exception;

import lombok.Getter;

/** Thrown when a state machine transition is illegal (e.g. SETTLED -> PENDING). HTTP 422. */
@Getter
public class InvalidTransactionStateException extends BankingDomainException {
    private final String currentState;
    private final String targetState;

    public InvalidTransactionStateException(String currentState, String targetState) {
        super("Cannot transition transaction from state '%s' to '%s'.".formatted(currentState, targetState));
        this.currentState = currentState;
        this.targetState = targetState;
    }
}