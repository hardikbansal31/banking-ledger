package com.bankingcore.bankingledger.domain.repository;

import com.bankingcore.bankingledger.domain.entity.Account;
import com.bankingcore.bankingledger.domain.entity.LedgerEntry;
import com.bankingcore.bankingledger.domain.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    /**
     * All entries for a transaction — should always return exactly 2.
     * If it returns anything other than 2, the ledger is broken.
     */
    List<LedgerEntry> findByTransactionOrderByCreatedAtAsc(Transaction transaction);

    /**
     * Paginated ledger statement for a single account — all entries
     * in reverse chronological order (newest first).
     */
    Page<LedgerEntry> findByAccountOrderByCreatedAtDesc(Account account,
                                                        Pageable pageable);
}