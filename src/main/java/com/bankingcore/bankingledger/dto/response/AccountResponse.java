package com.bankingcore.bankingledger.dto.response;

import com.bankingcore.bankingledger.domain.enums.AccountStatus;
import com.bankingcore.bankingledger.domain.enums.AccountType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for account endpoints.
 * Never exposes the owner's full User entity — only the ID.
 */
@Data
@Builder
public class AccountResponse {

    private UUID          id;
    private String        accountNumber;
    private AccountType   accountType;
    private AccountStatus status;
    private String        currency;
    private BigDecimal    balance;
    private BigDecimal    minimumBalance;
    private UUID          ownerId;
    private String        ownerUsername;
    private Instant       createdAt;
    private Instant       updatedAt;
}