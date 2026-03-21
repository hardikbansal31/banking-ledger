package com.bankingcore.bankingledger.domain.enums;

/**
 * TransactionStatus — states in the transaction lifecycle state machine.
 *
 * Legal transitions (enforced by TransactionStateMachine in Phase 4):
 *
 *   PENDING → AUTHORIZED   : funds reserved, lock acquired
 *   PENDING → FAILED        : validation failed before authorization
 *   AUTHORIZED → SETTLED    : ledger entries written, balances updated
 *   AUTHORIZED → FAILED     : something went wrong after authorization
 *   SETTLED → (terminal)    : no further transitions allowed
 *   FAILED  → (terminal)    : no further transitions allowed
 *
 * SETTLED and FAILED are terminal states — once reached, the transaction
 * record is effectively immutable (enforced at the service layer).
 *
 * Why PENDING → AUTHORIZED → SETTLED instead of just PENDING → SETTLED?
 * In real banking, authorization (reserving funds) and settlement (actual
 * movement) are separate steps that can happen seconds or days apart.
 * Card transactions authorize immediately but settle overnight in batch.
 * Keeping them separate makes the model accurate.
 */
public enum TransactionStatus {
    PENDING,
    AUTHORIZED,
    SETTLED,
    FAILED
}