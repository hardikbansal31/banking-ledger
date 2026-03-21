package com.bankingcore.bankingledger.domain.entity;

import com.bankingcore.bankingledger.domain.enums.TransactionStatus;
import com.bankingcore.bankingledger.domain.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Transaction — the header record for a financial movement.
 *
 * Think of it like a receipt: it describes WHAT happened, WHEN, HOW MUCH,
 * and between WHICH accounts. The actual accounting impact lives in the
 * two LedgerEntry rows that reference this transaction.
 *
 * Relationship:
 *   Transaction (1) ←→ (2) LedgerEntry
 *
 * Key design decisions:
 *
 * 1. idempotencyKey — a client-supplied unique key (e.g. UUID) that prevents
 *    duplicate transactions if the client retries a failed request. The DB
 *    unique constraint guarantees at-most-once processing even under concurrent
 *    retries. Always check for this in the service before processing.
 *
 * 2. amount uses BigDecimal(precision=19, scale=4) — 4 decimal places here
 *    (vs 2 on Account.balance) because exchange rate calculations need more
 *    precision before the final rounded amount is applied to balances.
 *
 * 3. failureReason — populated only when status = FAILED. Stored so that
 *    ops and support can see WHY a transaction failed without digging logs.
 *
 * 4. settledAt — separate from updatedAt because a transaction could be
 *    updated (e.g. metadata change) without being settled.
 *
 * 5. LedgerEntries are CascadeType.ALL — when a Transaction is persisted,
 *    its two LedgerEntry children are persisted in the same flush.
 *    orphanRemoval = true ensures entries are deleted if removed from the list
 *    (shouldn't happen in practice — entries are immutable).
 */
@Entity
@Table(
        name = "transactions",
        indexes = {
                @Index(name = "idx_txn_source_account",  columnList = "source_account_id"),
                @Index(name = "idx_txn_dest_account",    columnList = "destination_account_id"),
                @Index(name = "idx_txn_status",          columnList = "status"),
                @Index(name = "idx_txn_created_at",      columnList = "created_at"),
                @Index(name = "idx_txn_idempotency_key", columnList = "idempotency_key")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_txn_idempotency_key",
                        columnNames = "idempotency_key")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction extends BaseEntity {

    // ── Accounts involved ─────────────────────────────────────────────────────

    /**
     * The account being debited (money leaves here).
     * LAZY — we don't need the full Account graph to read transaction status.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_account_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_txn_source_account"))
    private Account sourceAccount;

    /**
     * The account being credited (money arrives here).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_account_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_txn_destination_account"))
    private Account destinationAccount;

    // ── Amount ────────────────────────────────────────────────────────────────

    /**
     * The amount being transferred, in the source account's currency.
     * Scale 4 for precision during exchange rate calculations.
     */
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /**
     * ISO 4217 currency code of the transaction amount.
     */
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    /**
     * Fee charged for this transaction (if any). Zero by default.
     * Populated by FeeEngine in Phase 5.
     */
    @Column(name = "fee_amount", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal feeAmount = BigDecimal.ZERO;

    // ── Classification ────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20)
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private TransactionStatus status = TransactionStatus.PENDING;

    // ── Idempotency ───────────────────────────────────────────────────────────

    /**
     * Client-supplied idempotency key. If the same key is submitted twice,
     * the second request returns the original transaction without processing again.
     * Clients should generate a UUID per transfer attempt and reuse it on retry.
     */
    @Column(name = "idempotency_key", nullable = false, length = 64, updatable = false)
    private String idempotencyKey;

    // ── Metadata ──────────────────────────────────────────────────────────────

    /**
     * Human-readable description (e.g. "Rent payment", "Invoice #42").
     */
    @Column(name = "description", length = 255)
    private String description;

    /**
     * Populated when status = FAILED. Records why the transaction failed
     * so ops can diagnose without digging through logs.
     */
    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    /**
     * UTC timestamp when this transaction reached SETTLED status.
     * Null until settlement occurs.
     */
    @Column(name = "settled_at")
    private Instant settledAt;

    // ── Child ledger entries ──────────────────────────────────────────────────

    /**
     * The two double-entry ledger rows for this transaction.
     * Always exactly 2 once settled: one DEBIT, one CREDIT.
     * Cascade ALL — entries are created and persisted with the transaction.
     */
    @OneToMany(mappedBy = "transaction",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<LedgerEntry> ledgerEntries = new ArrayList<>();

    // ── Business helpers ──────────────────────────────────────────────────────

    public boolean isPending()    { return status == TransactionStatus.PENDING; }
    public boolean isAuthorized() { return status == TransactionStatus.AUTHORIZED; }
    public boolean isSettled()    { return status == TransactionStatus.SETTLED; }
    public boolean isFailed()     { return status == TransactionStatus.FAILED; }
    public boolean isTerminal()   { return isSettled() || isFailed(); }
}