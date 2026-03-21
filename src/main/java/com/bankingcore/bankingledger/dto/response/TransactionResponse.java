package com.bankingcore.bankingledger.dto.response;

import com.bankingcore.bankingledger.domain.enums.EntryType;
import com.bankingcore.bankingledger.domain.enums.TransactionStatus;
import com.bankingcore.bankingledger.domain.enums.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class TransactionResponse {

    @Data
    @Builder
    public static class Detail {
        private UUID              id;
        private String            idempotencyKey;
        private TransactionType   transactionType;
        private TransactionStatus status;

        // Account references — number only, not full entity
        private String            sourceAccountNumber;
        private String            destinationAccountNumber;

        private BigDecimal        amount;
        private BigDecimal        feeAmount;
        private String            currency;
        private String            description;
        private String            failureReason;

        private Instant           createdAt;
        private Instant           settledAt;

        // The two ledger entries — always present once settled
        private List<LedgerEntryResponse> ledgerEntries;
    }

    @Data
    @Builder
    public static class Summary {
        private UUID              id;
        private TransactionType   transactionType;
        private TransactionStatus status;
        private String            sourceAccountNumber;
        private String            destinationAccountNumber;
        private BigDecimal        amount;
        private String            currency;
        private Instant           createdAt;
    }
}