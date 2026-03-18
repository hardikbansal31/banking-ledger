package com.bankingcore.bankingledger.exception;

/** Thrown when a Redisson lock cannot be acquired within timeout. HTTP 503. */
public class DistributedLockException extends BankingDomainException {
    public DistributedLockException(String lockKey) {
        super("Failed to acquire distributed lock for resource '%s'. Retry after a moment.".formatted(lockKey));
    }
    public DistributedLockException(String lockKey, Throwable cause) {
        super("Interrupted while waiting for lock on resource '%s'.".formatted(lockKey), cause);
    }
}