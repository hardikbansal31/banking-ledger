package com.bankingcore.bankingledger.domain.entity;

import com.bankingcore.bankingledger.domain.enums.EntryType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * LedgerEntry — one side of a double-entry accounting record.
 *
 * This is the most important entity in the entire system. Every financial
 * movement produces exactly two of these rows — one DEBIT and one CREDIT.
 * They are IMMUTABLE once written. Nothing in the application ever calls
 * UPDATE or DELETE on this table.
 *
 * Why immutable?
 *   Banking regulations (and basic accounting principles) require a complete,
 *   unalterable audit trail. If a transaction was wrong, you create a REVERSAL
 *   with two new entries in the opposite direction. You never modify history.
 *
 * The @PreUpdate guard enforces this in Java. The DB-level enforcement is the
 * absence of any UPDATE permission on this table in production (Phase 6).
 *
 * balanceAfter:
 *   Stores the account balance AFTER this entry was applied.
 *   This is denormalised data — you could compute it by replaying all entries —
 *   but storing it makes statement generation and auditing O(1) instead of O(n).
 *   It also lets you detect if something has tampered with the balance without
 *   a full ledger replay.
 */
@Entity
@Table(
        name = "ledger_entries",
        indexes = {
                @Index(name = "idx_entry_account",     columnList = "account_id"),
                @Index(name = "idx_entry_transaction", columnList = "transaction_id"),
                @Index(name = "idx_entry_created_at",  columnList = "created_at")
        }
)
@Getter
// Deliberately NO @Setter — immutability enforced at the Java level.
// The only way to set fields is via the @Builder or constructor.
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry extends BaseEntity {

    // ── Relationships ─────────────────────────────────────────────────────────

    /**
     * The account this entry affects.
     * LAZY — loading an entry doesn't need the full Account graph.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_entry_account"))
    private Account account;

    /**
     * The transaction this entry belongs to.
     * updatable = false — once set, the transaction reference never changes.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_entry_transaction"))
    private Transaction transaction;

    // ── Accounting fields ─────────────────────────────────────────────────────

    /**
     * DEBIT or CREDIT — the direction of this entry.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false, length = 10)
    private EntryType entryType;

    /**
     * The amount of this entry. Always positive.
     * Direction is conveyed by entryType, not by sign.
     * Scale 4 matches Transaction.amount for precision consistency.
     */
    @Column(name = "amount", nullable = false, updatable = false,
            precision = 19, scale = 4)
    private BigDecimal amount;

    /**
     * ISO 4217 currency code.
     */
    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    /**
     * The account balance AFTER this entry was applied.
     * Denormalised for O(1) statement and audit queries.
     * Scale 2 — this is a settled balance, already rounded.
     */
    @Column(name = "balance_after", nullable = false, updatable = false,
            precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    /**
     * Optional description carried from the parent Transaction.
     */
    @Column(name = "description", updatable = false, length = 255)
    private String description;

    // ── Immutability enforcement ──────────────────────────────────────────────

    /**
     * Prevents any UPDATE on this entity after the initial INSERT.
     * This is a Java-level guard; the DB-level guard is no UPDATE permission
     * on ledger_entries in production.
     *
     * @throws IllegalStateException if anything tries to UPDATE a ledger entry
     */
    @PreUpdate
    protected void onPreUpdate() {
        throw new IllegalStateException(
                "LedgerEntry is immutable — attempted UPDATE on entry id=" + getId()
                        + ". Create a REVERSAL transaction instead.");
    }
}