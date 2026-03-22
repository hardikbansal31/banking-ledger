package com.bankingcore.bankingledger.service;

import com.bankingcore.bankingledger.domain.enums.TransactionStatus;
import com.bankingcore.bankingledger.exception.InvalidTransactionStateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * TransactionStateMachine — enforces the legal transaction lifecycle.
 *
 * ALLOWED TRANSITIONS:
 *
 *   PENDING    → AUTHORIZED, FAILED
 *   AUTHORIZED → SETTLED, FAILED
 *   SETTLED    → (terminal — no further transitions)
 *   FAILED     → (terminal — no further transitions)
 *
 * WHY A STATE MACHINE MATTERS:
 *   Without enforced transitions, a bug could settle an already-failed
 *   transaction, or fail an already-settled one — leaving the ledger
 *   in an inconsistent state. The state machine makes illegal states
 *   impossible to reach, not just unlikely.
 *
 *   This is a classic pattern in payments systems. Visa and Mastercard
 *   processing networks have state machines with dozens of states.
 *   Ours is simplified but structurally identical.
 *
 * USAGE:
 *   stateMachine.assertCanTransition(transaction.getStatus(), SETTLED);
 *   // throws InvalidTransactionStateException if illegal
 *   transaction.setStatus(SETTLED);
 */
@Slf4j
@Component
public class TransactionStateMachine {

    /**
     * The complete transition table.
     * Key   = current state
     * Value = set of states that are legal to transition TO from the key
     */
    private static final Map<TransactionStatus, Set<TransactionStatus>> ALLOWED_TRANSITIONS;

    static {
        ALLOWED_TRANSITIONS = new EnumMap<>(TransactionStatus.class);

        ALLOWED_TRANSITIONS.put(
                TransactionStatus.PENDING,
                EnumSet.of(TransactionStatus.AUTHORIZED, TransactionStatus.FAILED)
        );

        ALLOWED_TRANSITIONS.put(
                TransactionStatus.AUTHORIZED,
                EnumSet.of(TransactionStatus.SETTLED, TransactionStatus.FAILED)
        );

        // Terminal states — empty set means no transitions allowed
        ALLOWED_TRANSITIONS.put(
                TransactionStatus.SETTLED,
                EnumSet.noneOf(TransactionStatus.class)
        );

        ALLOWED_TRANSITIONS.put(
                TransactionStatus.FAILED,
                EnumSet.noneOf(TransactionStatus.class)
        );
    }

    /**
     * Asserts that transitioning from {@code current} to {@code target} is legal.
     * Throws {@link InvalidTransactionStateException} if the transition is illegal.
     * This method has no side effects — it only validates.
     *
     * @param current the transaction's current status
     * @param target  the status you want to transition to
     */
    public void assertCanTransition(TransactionStatus current, TransactionStatus target) {
        Set<TransactionStatus> allowed = ALLOWED_TRANSITIONS.get(current);

        if (allowed == null || !allowed.contains(target)) {
            log.warn("Illegal state transition attempted: {} → {}", current, target);
            throw new InvalidTransactionStateException(current.name(), target.name());
        }

        log.debug("State transition validated: {} → {}", current, target);
    }

    /**
     * Returns true if the transition is legal, false otherwise.
     * Use this for conditional logic; use assertCanTransition() for hard requirements.
     */
    public boolean canTransition(TransactionStatus current, TransactionStatus target) {
        Set<TransactionStatus> allowed = ALLOWED_TRANSITIONS.get(current);
        return allowed != null && allowed.contains(target);
    }

    /**
     * Returns true if the status is terminal (SETTLED or FAILED).
     * Terminal transactions cannot be modified in any way.
     */
    public boolean isTerminal(TransactionStatus status) {
        Set<TransactionStatus> allowed = ALLOWED_TRANSITIONS.get(status);
        return allowed != null && allowed.isEmpty();
    }
}