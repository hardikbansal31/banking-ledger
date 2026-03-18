package com.bankingcore.bankingledger.domain.enums;

/**
 * Lifecycle states for a bank account.
 *
 * PENDING_VERIFICATION : created but not yet KYC-approved
 * ACTIVE               : normal operating state
 * FROZEN               : temporarily blocked (fraud hold, regulatory)
 * CLOSED               : permanently closed — soft-deleted equivalent for accounts
 */
public enum AccountStatus {
    PENDING_VERIFICATION,
    ACTIVE,
    FROZEN,
    CLOSED
}