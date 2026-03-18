package com.bankingcore.bankingledger.domain.enums;

/**
 * Types of bank accounts supported by the ledger.
 */
public enum AccountType {
    CHECKING,
    SAVINGS,
    INVESTMENT,
    /** Internal suspense/escrow account used by the ledger itself */
    INTERNAL
}