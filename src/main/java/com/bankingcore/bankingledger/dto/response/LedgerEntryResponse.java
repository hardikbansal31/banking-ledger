package com.bankingcore.bankingledger.dto.response;

import com.bankingcore.bankingledger.domain.enums.EntryType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class LedgerEntryResponse {
    private UUID       id;
    private String     accountNumber;
    private EntryType  entryType;
    private BigDecimal amount;
    private String     currency;
    private BigDecimal balanceAfter;
    private String     description;
    private Instant    createdAt;
}