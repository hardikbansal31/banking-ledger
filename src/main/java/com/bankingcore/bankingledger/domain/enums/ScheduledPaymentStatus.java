package com.bankingcore.bankingledger.domain.enums;

/**
 * ScheduledPaymentStatus — lifecycle of a recurring scheduled payment.
 *
 * ACTIVE   : job exists in Quartz, fires on schedule
 * PAUSED   : job exists but is paused (won't fire until resumed)
 * CANCELLED: job removed from Quartz, no more executions
 * FAILED   : last execution failed (Quartz may or may not retry)
 */
public enum ScheduledPaymentStatus {
    ACTIVE,
    PAUSED,
    CANCELLED,
    FAILED
}