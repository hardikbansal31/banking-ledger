package com.bankingcore.bankingledger.domain.entity;

import com.bankingcore.bankingledger.domain.enums.ScheduledPaymentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * ScheduledPayment — configuration record for a recurring transfer.
 *
 * When created, a Quartz job is registered with the given cronExpression.
 * The job calls LedgerService.transfer() on each fire.
 *
 * The Quartz job ID is stored as jobKey so it can be paused/cancelled.
 *
 * Example cron expressions:
 *   "0 0 9 1 * ?"     — 9am on the 1st of every month (rent)
 *   "0 0 8 ? * MON"   — 8am every Monday
 *   "0 0 12 * * ?"    — noon every day
 */
@Entity
@Table(
        name = "scheduled_payments",
        indexes = {
                @Index(name = "idx_scheduled_source", columnList = "source_account_id"),
                @Index(name = "idx_scheduled_status", columnList = "status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduledPayment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_account_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_scheduled_source"))
    private Account sourceAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_account_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_scheduled_destination"))
    private Account destinationAccount;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "description", length = 255)
    private String description;

    /** Quartz cron expression — e.g. "0 0 9 1 * ?" */
    @Column(name = "cron_expression", nullable = false, length = 100)
    private String cronExpression;

    /** Quartz job key — used to pause/resume/cancel the job */
    @Column(name = "job_key", nullable = false, length = 100, unique = true)
    private String jobKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ScheduledPaymentStatus status = ScheduledPaymentStatus.ACTIVE;

    /** UTC timestamp of the last successful execution */
    @Column(name = "last_executed_at")
    private Instant lastExecutedAt;

    /** UTC timestamp of next scheduled execution (informational — Quartz owns this) */
    @Column(name = "next_fire_at")
    private Instant nextFireAt;

    /** Count of successful executions */
    @Column(name = "execution_count", nullable = false)
    @Builder.Default
    private int executionCount = 0;
}