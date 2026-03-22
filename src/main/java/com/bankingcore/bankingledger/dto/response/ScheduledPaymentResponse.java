package com.bankingcore.bankingledger.dto.response;

import com.bankingcore.bankingledger.domain.enums.ScheduledPaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ScheduledPaymentResponse {
    private UUID                   id;
    private String                 sourceAccountNumber;
    private String                 destinationAccountNumber;
    private BigDecimal             amount;
    private String                 currency;
    private String                 description;
    private String                 cronExpression;
    private ScheduledPaymentStatus status;
    private Instant                lastExecutedAt;
    private Instant                nextFireAt;
    private int                    executionCount;
    private Instant                createdAt;
}