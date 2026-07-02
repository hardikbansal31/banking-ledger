package com.bankingcore.bankingledger.service;

import com.bankingcore.bankingledger.config.FeeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;

class FeeEngineTest {

    private FeeEngine feeEngine;

    @BeforeEach
    void setUp() {
        FeeProperties props = new FeeProperties();
        props.setTierLowThreshold(new BigDecimal("1000.00"));
        props.setTierHighThreshold(new BigDecimal("10000.00"));
        props.setTierLowRate(new BigDecimal("0.0150")); // 1.50%
        props.setTierMidRate(new BigDecimal("0.0100")); // 1.00%
        props.setTierHighRate(new BigDecimal("0.0050")); // 0.50%

        feeEngine = new FeeEngine(props);
    }

    // Guards against: Boundary bug where $10,000 gets charged mid-tier instead of high-tier
    @Test
    void calculateFee_shouldApplyHighTier_whenAmountIsExactly10000() {
        BigDecimal amount = new BigDecimal("10000.00");
        BigDecimal expectedFee = amount.multiply(new BigDecimal("0.0050"))
                                       .setScale(4, RoundingMode.HALF_EVEN);

        BigDecimal result = feeEngine.calculateFee(amount, "USD");

        assertThat(result).isEqualByComparingTo(expectedFee); // 50.0000
    }

    // Guards against: Basic calculation failure in the common mid-tier range
    @Test
    void calculateFee_shouldApplyMidTier_whenAmountIsBetween1000And9999() {
        BigDecimal amount = new BigDecimal("5000.00");
        BigDecimal expectedFee = amount.multiply(new BigDecimal("0.0100"))
                                       .setScale(4, RoundingMode.HALF_EVEN);

        BigDecimal result = feeEngine.calculateFee(amount, "USD");

        assertThat(result).isEqualByComparingTo(expectedFee); // 50.0000
    }

    // Guards against: Boundary bug where amounts under $1000 are not taxed properly
    @Test
    void calculateFee_shouldApplyLowTier_whenAmountIsUnder1000() {
        BigDecimal amount = new BigDecimal("999.99");
        BigDecimal expectedFee = amount.multiply(new BigDecimal("0.0150"))
                                       .setScale(4, RoundingMode.HALF_EVEN);

        BigDecimal result = feeEngine.calculateFee(amount, "USD");

        assertThat(result).isEqualByComparingTo(expectedFee);
    }

    // Guards against: Floating point rounding anomalies causing fractional cents to round incorrectly
    @Test
    void calculateFee_shouldUseHalfEvenRounding_whenFractionalCentsOccur() {
        BigDecimal amount = new BigDecimal("100.55");
        // 100.55 * 0.0150 = 1.50825. HALF_EVEN rounds 5 to nearest even -> 1.5082
        BigDecimal expectedFee = new BigDecimal("1.5082");

        BigDecimal result = feeEngine.calculateFee(amount, "USD");

        assertThat(result).isEqualByComparingTo(expectedFee);
    }

    // Guards against: Negative amount exploit where a user could earn money on a transfer
    @Test
    void calculateFee_shouldReturnZero_whenAmountIsZeroOrNegative() {
        BigDecimal resultZero = feeEngine.calculateFee(BigDecimal.ZERO, "USD");
        BigDecimal resultNegative = feeEngine.calculateFee(new BigDecimal("-500.00"), "USD");

        assertThat(resultZero).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resultNegative).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
