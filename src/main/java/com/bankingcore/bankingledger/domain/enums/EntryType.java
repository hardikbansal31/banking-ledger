package com.bankingcore.bankingledger.domain.enums;

/**
 * EntryType — the direction of a single ledger entry.
 *
 * DEBIT  : reduces the account balance (money leaving an account)
 * CREDIT : increases the account balance (money entering an account)
 *
 * Every Transaction produces exactly two LedgerEntry rows:
 *   one DEBIT  on the source account
 *   one CREDIT on the destination account
 *
 * The sum of all DEBITs must always equal the sum of all CREDITs
 * across the entire ledger. This invariant is what "balanced books" means.
 */
public enum EntryType {
    DEBIT,
    CREDIT
}