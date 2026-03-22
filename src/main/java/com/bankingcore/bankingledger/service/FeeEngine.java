package com.bankingcore.bankingledger.service;

import com.bankingcore.bankingledger.config.FeeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * FeeEngine — calculates the platform fee for a transaction.
 *
 * TIER LOGIC:
 *   amount < 1,000        → 1.50% fee  (small transfers, highest rate)
 *   1,000 <= amount < 10,000 → 1.00% fee  (mid-range)
 *   amount >= 10,000      → 0.50% fee  (large transfers, lowest rate)
 *
 * WHY BigDecimal AND NOT double:
 *   double cannot represent 0.1 exactly in binary floating-point.
 *   0.1 + 0.2 = 0.30000000000000004 in double arithmetic.
 *   On a $10,000 transfer: 10000 * 0.015 = 150.00 in real life.
 *   In double: 10000.0 * 0.015 = 149.99999999999997 — wrong by a penny.
 *   A penny error per transaction, millions of transactions per day =
 *   regulatory violation and potential fraud liability.
 *   BigDecimal is exact. Always use it for money.
 *
 * ROUNDING:
 *   RoundingMode.HALF_EVEN (banker's rounding) — rounds 0.5 to the
 *   nearest even digit. Over many transactions this is statistically
 *   neutral (no systematic bias toward rounding up or down).
 *   It's the standard rounding mode for financial calculations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeeEngine {

    private final FeeProperties feeProperties;

    /**
     * Calculates the fee for transferring {@code amount} in {@code currency}.
     *
     * @param amount    the transfer amount (must be positive)
     * @param currency  ISO 4217 currency code (reserved for future per-currency rates)
     * @return fee amount, scaled to 4 decimal places, never negative
     */
    public BigDecimal calculateFee(BigDecimal amount, String currency) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("FeeEngine called with non-positive amount: {}", amount);
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN);
        }

        BigDecimal rate = determineRate(amount);
        BigDecimal fee  = amount.multiply(rate).setScale(4, RoundingMode.HALF_EVEN);

        log.debug("Fee calculation: amount={} currency={} rate={} fee={}",
                amount, currency, rate, fee);

        return fee;
    }

    /**
     * Returns the fee rate for the given amount based on tier thresholds.
     */
    private BigDecimal determineRate(BigDecimal amount) {
        if (amount.compareTo(feeProperties.getTierLowThreshold()) < 0) {
            log.debug("Amount {} below low threshold {} — applying low tier rate {}",
                    amount, feeProperties.getTierLowThreshold(), feeProperties.getTierLowRate());
            return feeProperties.getTierLowRate();
        }
        if (amount.compareTo(feeProperties.getTierHighThreshold()) < 0) {
            log.debug("Amount {} in mid tier — applying mid rate {}",
                    amount, feeProperties.getTierMidRate());
            return feeProperties.getTierMidRate();
        }
        log.debug("Amount {} at or above high threshold {} — applying high tier rate {}",
                amount, feeProperties.getTierHighThreshold(), feeProperties.getTierHighRate());
        return feeProperties.getTierHighRate();
    }

    /**
     * Returns a human-readable description of which tier applies.
     * Used in transaction descriptions and logs.
     */
    public String describeTier(BigDecimal amount) {
        if (amount.compareTo(feeProperties.getTierLowThreshold()) < 0) {
            return "standard tier (%.2f%%)".formatted(
                    feeProperties.getTierLowRate().multiply(BigDecimal.valueOf(100)));
        }
        if (amount.compareTo(feeProperties.getTierHighThreshold()) < 0) {
            return "preferred tier (%.2f%%)".formatted(
                    feeProperties.getTierMidRate().multiply(BigDecimal.valueOf(100)));
        }
        return "premium tier (%.2f%%)".formatted(
                feeProperties.getTierHighRate().multiply(BigDecimal.valueOf(100)));
    }
}