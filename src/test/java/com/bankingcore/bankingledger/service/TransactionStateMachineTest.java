package com.bankingcore.bankingledger.service;

import com.bankingcore.bankingledger.domain.enums.TransactionStatus;
import com.bankingcore.bankingledger.exception.InvalidTransactionStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransactionStateMachineTest {

    private TransactionStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        stateMachine = new TransactionStateMachine();
    }

    // Guards against: Basic state transition failure blocking valid transaction flows
    @Test
    void assertCanTransition_shouldSucceed_whenPendingToAuthorized() {
        // Should not throw any exception
        stateMachine.assertCanTransition(TransactionStatus.PENDING, TransactionStatus.AUTHORIZED);
    }

    // Guards against: Reversing transaction states illegally (e.g., AUTHORIZED back to PENDING)
    @Test
    void assertCanTransition_shouldThrowException_whenAuthorizedToPending() {
        assertThatThrownBy(() -> 
            stateMachine.assertCanTransition(TransactionStatus.AUTHORIZED, TransactionStatus.PENDING))
            .isInstanceOf(InvalidTransactionStateException.class);
    }

    // Guards against: Modifying terminal states, which would violate ledger immutability
    @Test
    void assertCanTransition_shouldThrowException_whenTargetStateIsTerminal() {
        // SETTLED is terminal
        assertThatThrownBy(() -> 
            stateMachine.assertCanTransition(TransactionStatus.SETTLED, TransactionStatus.FAILED))
            .isInstanceOf(InvalidTransactionStateException.class);
            
        // FAILED is terminal
        assertThatThrownBy(() -> 
            stateMachine.assertCanTransition(TransactionStatus.FAILED, TransactionStatus.PENDING))
            .isInstanceOf(InvalidTransactionStateException.class);
    }

    // Guards against: Incorrect classification of terminal states allowing unauthorized updates
    @Test
    void isTerminal_shouldReturnTrue_forSettledAndFailed() {
        assertThat(stateMachine.isTerminal(TransactionStatus.SETTLED)).isTrue();
        assertThat(stateMachine.isTerminal(TransactionStatus.FAILED)).isTrue();
        assertThat(stateMachine.isTerminal(TransactionStatus.PENDING)).isFalse();
        assertThat(stateMachine.isTerminal(TransactionStatus.AUTHORIZED)).isFalse();
    }
}
