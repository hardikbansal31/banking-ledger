package com.bankingcore.bankingledger.domain.repository;

import com.bankingcore.bankingledger.domain.entity.Account;
import com.bankingcore.bankingledger.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * AccountRepository — data access for the accounts table.
 *
 * Note on PESSIMISTIC_WRITE:
 * Phase 4 uses Redisson distributed locks as the PRIMARY concurrency guard.
 * findByIdForUpdate is a secondary DB-level lock used inside the same
 * transaction for extra safety. Both locks together prevent double-spending
 * even under network partition scenarios.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByAccountNumber(String accountNumber);

    List<Account> findAllByOwner(User owner);

    boolean existsByAccountNumber(String accountNumber);

    /**
     * Acquires a pessimistic write lock on the account row for the duration
     * of the calling @Transactional method. Used during debit/credit operations
     * alongside the Redisson distributed lock in Phase 4.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);
}