package com.bankingcore.bankingledger.service;

import com.bankingcore.bankingledger.exception.DistributedLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * DistributedLockService — wraps Redisson RLock into a clean, reusable API.
 *
 * WHY REDISSON OVER SPRING'S @Cacheable LOCK?
 *   Spring's built-in locks are JVM-local (in-process only). Redisson uses
 *   Redis as the coordination point, so locks work across multiple app
 *   instances in a cluster — which is the real production scenario.
 *
 * LOCK KEY CONVENTIONS:
 *   account:{accountNumber}      — locks a single account for debit/credit
 *   transfer:{sortedIds}         — locks two accounts together for a transfer
 *   transaction:{idempotencyKey} — prevents duplicate processing
 *
 * WAIT vs FAIL-FAST:
 *   waitTimeSeconds = 5   — try to acquire for up to 5 seconds before giving up
 *   leaseTimeSeconds = 30 — automatically release after 30s even if app crashes
 *                           (prevents lock starvation from dead processes)
 *
 * The leaseTime is critical. Without it, a JVM crash while holding a lock
 * would leave the lock in Redis forever, permanently blocking that account.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedLockService {

    private static final String LOCK_PREFIX       = "banking:lock:";
    private static final int    WAIT_TIME_SECONDS  = 5;
    private static final int    LEASE_TIME_SECONDS = 30;

    private final RedissonClient redissonClient;

    /**
     * Executes the given supplier while holding a distributed lock.
     * The lock is always released in a finally block — even on exception.
     *
     * Usage:
     * <pre>{@code
     * TransactionResponse result = lockService.executeWithLock(
     *     "account:ACC-000001",
     *     () -> ledgerService.transfer(request, username)
     * );
     * }</pre>
     *
     * @param lockKey  unique key identifying the resource to lock
     * @param action   the operation to perform while holding the lock
     * @return result of the action
     * @throws DistributedLockException if the lock cannot be acquired
     */
    public <T> T executeWithLock(String lockKey, Supplier<T> action) {
        String fullKey = LOCK_PREFIX + lockKey;
        RLock lock = redissonClient.getLock(fullKey);

        log.debug("Attempting to acquire lock: '{}'", fullKey);

        boolean acquired = false;
        try {
            acquired = lock.tryLock(WAIT_TIME_SECONDS, LEASE_TIME_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DistributedLockException(lockKey, e);
        }

        if (!acquired) {
            log.warn("Failed to acquire lock '{}' within {}s — resource contended",
                    fullKey, WAIT_TIME_SECONDS);
            throw new DistributedLockException(lockKey);
        }

        log.debug("Lock acquired: '{}'", fullKey);
        try {
            return action.get();
        } finally {
            // Only release if this thread holds the lock — prevents
            // releasing a lock that was auto-expired by Redis
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock released: '{}'", fullKey);
            } else {
                log.warn("Lock '{}' expired before release — operation took >{} seconds",
                        fullKey, LEASE_TIME_SECONDS);
            }
        }
    }

    /**
     * Builds a canonical lock key for a transfer between two accounts.
     * Sorts the account numbers so that A→B and B→A always produce the
     * same key, preventing two simultaneous opposite transfers from
     * each acquiring one of two locks and deadlocking.
     */
    public static String transferLockKey(String accountNumberA, String accountNumberB) {
        // Lexicographic sort ensures consistent ordering regardless of direction
        if (accountNumberA.compareTo(accountNumberB) <= 0) {
            return "transfer:" + accountNumberA + ":" + accountNumberB;
        }
        return "transfer:" + accountNumberB + ":" + accountNumberA;
    }

    /** Lock key for a single account operation (deposit, withdrawal). */
    public static String accountLockKey(String accountNumber) {
        return "account:" + accountNumber;
    }
}