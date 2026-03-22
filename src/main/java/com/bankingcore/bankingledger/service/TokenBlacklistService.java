package com.bankingcore.bankingledger.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Date;

/**
 * TokenBlacklistService — revokes JWT tokens before their natural expiry.
 *
 * Uses RedissonClient directly (RBucket) instead of StringRedisTemplate.
 * This avoids the Spring Boot Redis auto-config vs Redisson starter conflict
 * where StringRedisTemplate may connect without the password.
 *
 * Key format : banking:blacklist:{token}
 * Value      : "revoked"
 * TTL        : remaining token lifetime — Redis auto-deletes at expiry
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String BLACKLIST_PREFIX = "banking:blacklist:";

    private final RedissonClient redissonClient;

    public void blacklist(String token, Date expiresAt) {
        long remainingMs = expiresAt.getTime() - System.currentTimeMillis();

        if (remainingMs <= 0) {
            log.debug("Token already expired — skipping blacklist");
            return;
        }

        String key = BLACKLIST_PREFIX + token;
        RBucket<String> bucket = redissonClient.getBucket(key);
        bucket.set("revoked", Duration.ofMillis(remainingMs));

        log.info("Token blacklisted — expires in {}s", remainingMs / 1000);
    }

    public boolean isBlacklisted(String token) {
        String key = BLACKLIST_PREFIX + token;
        RBucket<String> bucket = redissonClient.getBucket(key);
        boolean blacklisted = bucket.isExists();
        if (blacklisted) {
            log.warn("Blacklisted token rejected");
        }
        return blacklisted;
    }
}