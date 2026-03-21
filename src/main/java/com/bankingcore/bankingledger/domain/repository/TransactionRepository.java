package com.bankingcore.bankingledger.domain.repository;

import com.bankingcore.bankingledger.domain.entity.Account;
import com.bankingcore.bankingledger.domain.entity.Transaction;
import com.bankingcore.bankingledger.domain.enums.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    /** Idempotency check — used before processing any new transaction */
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * All transactions where an account is either the source or destination.
     * Used to build account statements. Paginated — never load all transactions.
     */
    @Query("""
            SELECT t FROM Transaction t
            WHERE t.sourceAccount = :account
               OR t.destinationAccount = :account
            ORDER BY t.createdAt DESC
            """)
    Page<Transaction> findByAccount(@Param("account") Account account,
                                    Pageable pageable);

    /**
     * Transactions for an account filtered by status.
     * Useful for admin dashboards showing pending or failed transactions.
     */
    @Query("""
            SELECT t FROM Transaction t
            WHERE (t.sourceAccount = :account OR t.destinationAccount = :account)
              AND t.status = :status
            ORDER BY t.createdAt DESC
            """)
    Page<Transaction> findByAccountAndStatus(@Param("account") Account account,
                                             @Param("status") TransactionStatus status,
                                             Pageable pageable);
}