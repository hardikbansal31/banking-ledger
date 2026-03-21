package com.bankingcore.bankingledger.domain.enums;

/**
 * TransactionType — the nature of the financial movement.
 *
 * TRANSFER     : money moves between two user accounts (most common)
 * DEPOSIT      : money enters the system (e.g. from external bank)
 * WITHDRAWAL   : money leaves the system (e.g. to external bank)
 * FEE          : platform fee charged — debit from user, credit to internal account
 * REVERSAL     : cancels a prior SETTLED transaction with opposite entries
 * SCHEDULED    : a TRANSFER triggered by a Quartz scheduled job (Phase 5)
 */
public enum TransactionType {
    TRANSFER,
    DEPOSIT,
    WITHDRAWAL,
    FEE,
    REVERSAL,
    SCHEDULED
}