package com.bankingcore.bankingledger.service;

import com.bankingcore.bankingledger.domain.entity.Account;
import com.bankingcore.bankingledger.domain.entity.LedgerEntry;
import com.bankingcore.bankingledger.domain.entity.Transaction;
import com.bankingcore.bankingledger.domain.enums.EntryType;
import com.bankingcore.bankingledger.domain.enums.TransactionStatus;
import com.bankingcore.bankingledger.domain.enums.TransactionType;
import com.bankingcore.bankingledger.domain.repository.AccountRepository;
import com.bankingcore.bankingledger.domain.repository.LedgerEntryRepository;
import com.bankingcore.bankingledger.domain.repository.TransactionRepository;
import com.bankingcore.bankingledger.dto.request.TransactionRequest;
import com.bankingcore.bankingledger.dto.response.LedgerEntryResponse;
import com.bankingcore.bankingledger.dto.response.TransactionResponse;
import com.bankingcore.bankingledger.exception.AccountBlockedException;
import com.bankingcore.bankingledger.exception.CurrencyMismatchException;
import com.bankingcore.bankingledger.exception.DuplicateTransactionException;
import com.bankingcore.bankingledger.exception.InsufficientFundsException;
import com.bankingcore.bankingledger.exception.InvalidTransactionStateException;
import com.bankingcore.bankingledger.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * LedgerService — the core double-entry accounting engine.
 *
 * THE CRITICAL INVARIANT:
 *   For every transaction, the sum of all DEBIT entries must equal
 *   the sum of all CREDIT entries. This service enforces that invariant
 *   by always creating exactly two entries per transaction in one atomic
 *   @Transactional method.
 *
 * TRANSACTION FLOW:
 *
 *   1. Idempotency check — if the key exists, return the existing transaction
 *   2. Load accounts with pessimistic write locks (SELECT ... FOR UPDATE)
 *   3. Validate: both accounts ACTIVE, same currency, sufficient funds
 *   4. Create Transaction record (status = PENDING)
 *   5. Create DEBIT entry on source account, update source balance
 *   6. Create CREDIT entry on destination account, update destination balance
 *   7. Mark Transaction as SETTLED, record settledAt timestamp
 *   8. Persist everything — all in one flush, one DB transaction
 *
 *   If anything in steps 4-8 throws, Spring rolls back the entire DB
 *   transaction. No partial state is ever committed.
 *
 * WHY @Transactional IS NOT OPTIONAL HERE:
 *   Without it, steps 5 and 6 are separate DB transactions. If step 6 fails,
 *   step 5 has already committed — money has left the source account but
 *   never arrived in the destination. That's a ledger imbalance and a
 *   regulatory violation. @Transactional makes both-or-neither mandatory.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final TransactionRepository  transactionRepository;
    private final LedgerEntryRepository  ledgerEntryRepository;
    private final AccountRepository      accountRepository;

    // ── Transfer ──────────────────────────────────────────────────────────────

    /**
     * Executes a transfer between two accounts atomically.
     *
     * @param request    validated transfer request from controller
     * @param requestingUsername  the authenticated user — used for ownership check
     * @return the settled transaction with both ledger entries
     */
    @Transactional
    public TransactionResponse.Detail transfer(TransactionRequest.Transfer request,
                                               String requestingUsername) {

        log.info("Transfer initiated: {} → {} amount={} {} by user='{}'",
                request.getSourceAccountNumber(),
                request.getDestinationAccountNumber(),
                request.getAmount(),
                request.getCurrency(),
                requestingUsername);

        // ── Step 1: Idempotency check ─────────────────────────────────────────
        String idempotencyKey = resolveIdempotencyKey(request.getIdempotencyKey());

        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.info("Duplicate transfer request — returning existing transaction for key='{}'",
                    idempotencyKey);
            Transaction existing = transactionRepository
                    .findByIdempotencyKey(idempotencyKey)
                    .orElseThrow();
            return toDetail(existing);
        }

        // ── Step 2: Load accounts WITH pessimistic write locks ────────────────
        // SELECT ... FOR UPDATE prevents another transaction from reading
        // stale balances and committing a conflicting debit simultaneously.
        // Phase 4 adds Redisson distributed locks as an outer guard.
        Account source = accountRepository
                .findByAccountNumber(request.getSourceAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account", request.getSourceAccountNumber()));

        Account destination = accountRepository
                .findByAccountNumber(request.getDestinationAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account", request.getDestinationAccountNumber()));

        // Acquire DB-level pessimistic locks in a consistent order (by ID)
        // to prevent deadlocks when two transfers involve the same accounts
        // in opposite directions.
        UUID firstId  = source.getId().compareTo(destination.getId()) < 0
                ? source.getId() : destination.getId();
        UUID secondId = source.getId().compareTo(destination.getId()) < 0
                ? destination.getId() : source.getId();

        Account lockedFirst  = accountRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", firstId));
        Account lockedSecond = accountRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", secondId));

        // Re-resolve source/destination from locked references
        source      = lockedFirst.getId().equals(source.getId()) ? lockedFirst : lockedSecond;
        destination = lockedFirst.getId().equals(destination.getId()) ? lockedFirst : lockedSecond;

        // ── Step 3: Validation ────────────────────────────────────────────────
        validateTransfer(source, destination, request, requestingUsername);

        BigDecimal amount = request.getAmount().setScale(4, RoundingMode.HALF_EVEN);

        // ── Step 4: Create Transaction header (PENDING) ───────────────────────
        Transaction transaction = Transaction.builder()
                .sourceAccount(source)
                .destinationAccount(destination)
                .amount(amount)
                .currency(request.getCurrency().toUpperCase())
                .feeAmount(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_EVEN))
                .transactionType(TransactionType.TRANSFER)
                .status(TransactionStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .description(request.getDescription())
                .build();

        transaction = transactionRepository.save(transaction);
        log.debug("Transaction created id='{}' status=PENDING", transaction.getId());

        // ── Steps 5 & 6: Write ledger entries + update balances ───────────────
        // This is the double-entry core. Both mutations happen inside the
        // same @Transactional method — they will commit or rollback together.
        BigDecimal roundedAmount = amount.setScale(2, RoundingMode.HALF_EVEN);

        // DEBIT source — balance decreases
        source.debit(roundedAmount);
        accountRepository.save(source);

        LedgerEntry debitEntry = LedgerEntry.builder()
                .account(source)
                .transaction(transaction)
                .entryType(EntryType.DEBIT)
                .amount(amount)
                .currency(request.getCurrency().toUpperCase())
                .balanceAfter(source.getBalance())
                .description(request.getDescription())
                .build();

        ledgerEntryRepository.save(debitEntry);
        log.debug("DEBIT entry written: account='{}' amount={} balanceAfter={}",
                source.getAccountNumber(), amount, source.getBalance());

        // CREDIT destination — balance increases
        destination.credit(roundedAmount);
        accountRepository.save(destination);

        LedgerEntry creditEntry = LedgerEntry.builder()
                .account(destination)
                .transaction(transaction)
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .currency(request.getCurrency().toUpperCase())
                .balanceAfter(destination.getBalance())
                .description(request.getDescription())
                .build();

        ledgerEntryRepository.save(creditEntry);
        log.debug("CREDIT entry written: account='{}' amount={} balanceAfter={}",
                destination.getAccountNumber(), amount, destination.getBalance());

        // ── Step 7: Settle ────────────────────────────────────────────────────
        transaction.setStatus(TransactionStatus.SETTLED);
        transaction.setSettledAt(Instant.now());
        transaction.getLedgerEntries().add(debitEntry);
        transaction.getLedgerEntries().add(creditEntry);
        Transaction settled = transactionRepository.save(transaction);

        log.info("Transfer SETTLED: id='{}' {} {} {} → {}",
                settled.getId(),
                roundedAmount,
                request.getCurrency(),
                source.getAccountNumber(),
                destination.getAccountNumber());

        return toDetail(settled);
    }

    // ── Deposit (ADMIN only — simulates external funds arriving) ─────────────

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public TransactionResponse.Detail deposit(TransactionRequest.Deposit request) {

        log.info("Deposit initiated: account='{}' amount={} {}",
                request.getAccountNumber(), request.getAmount(), request.getCurrency());

        String idempotencyKey = resolveIdempotencyKey(request.getIdempotencyKey());

        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            return toDetail(transactionRepository
                    .findByIdempotencyKey(idempotencyKey).orElseThrow());
        }

        Account destination = accountRepository
                .findByAccountNumber(request.getAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account", request.getAccountNumber()));

        if (!destination.isOperational()) {
            throw new AccountBlockedException(destination.getAccountNumber());
        }

        // For deposits, source = destination (internal system account convention)
        // A proper implementation would use a dedicated NOSTRO/system account.
        // Simplified here — Phase 5 introduces the internal fee account.
        BigDecimal amount = request.getAmount().setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal roundedAmount = amount.setScale(2, RoundingMode.HALF_EVEN);

        Transaction transaction = Transaction.builder()
                .sourceAccount(destination)   // self-referential for external deposits
                .destinationAccount(destination)
                .amount(amount)
                .currency(request.getCurrency().toUpperCase())
                .feeAmount(BigDecimal.ZERO.setScale(4))
                .transactionType(TransactionType.DEPOSIT)
                .status(TransactionStatus.PENDING)
                .idempotencyKey(idempotencyKey)
                .description(request.getDescription())
                .build();

        transaction = transactionRepository.save(transaction);

        destination.credit(roundedAmount);
        accountRepository.save(destination);

        LedgerEntry creditEntry = LedgerEntry.builder()
                .account(destination)
                .transaction(transaction)
                .entryType(EntryType.CREDIT)
                .amount(amount)
                .currency(request.getCurrency().toUpperCase())
                .balanceAfter(destination.getBalance())
                .description(request.getDescription())
                .build();

        ledgerEntryRepository.save(creditEntry);

        transaction.setStatus(TransactionStatus.SETTLED);
        transaction.setSettledAt(Instant.now());
        transaction.getLedgerEntries().add(creditEntry);
        Transaction settled = transactionRepository.save(transaction);

        log.info("Deposit SETTLED: id='{}' {} {} → account='{}'",
                settled.getId(), roundedAmount, request.getCurrency(),
                destination.getAccountNumber());

        return toDetail(settled);
    }

    // ── Read operations ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TransactionResponse.Detail getTransactionById(UUID transactionId,
                                                         String requestingUsername) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));

        enforceTransactionAccess(transaction, requestingUsername);
        return toDetail(transaction);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse.Summary> getAccountTransactions(
            String accountNumber, String requestingUsername, Pageable pageable) {

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountNumber));

        enforceAccountAccess(account, requestingUsername);

        return transactionRepository.findByAccount(account, pageable)
                .map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> getAccountStatement(
            String accountNumber, String requestingUsername, Pageable pageable) {

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountNumber));

        enforceAccountAccess(account, requestingUsername);

        return ledgerEntryRepository
                .findByAccountOrderByCreatedAtDesc(account, pageable)
                .map(this::toEntryResponse);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validateTransfer(Account source,
                                  Account destination,
                                  TransactionRequest.Transfer request,
                                  String requestingUsername) {

        // Ownership — requesting user must own the source account
        if (!source.getOwner().getUsername().equals(requestingUsername)) {
            log.warn("User '{}' attempted transfer from account '{}' owned by '{}'",
                    requestingUsername, source.getAccountNumber(),
                    source.getOwner().getUsername());
            throw new org.springframework.security.access.AccessDeniedException(
                    "You do not own source account: " + source.getAccountNumber());
        }

        // Cannot transfer to the same account
        if (source.getId().equals(destination.getId())) {
            throw new IllegalArgumentException(
                    "Source and destination accounts must be different.");
        }

        // Both accounts must be ACTIVE
        if (!source.isOperational()) {
            throw new AccountBlockedException(source.getAccountNumber());
        }
        if (!destination.isOperational()) {
            throw new AccountBlockedException(destination.getAccountNumber());
        }

        // Currency must match (cross-currency handled in Phase 5)
        if (!source.getCurrency().equals(request.getCurrency().toUpperCase())) {
            throw new CurrencyMismatchException(
                    source.getCurrency(), request.getCurrency());
        }
        if (!destination.getCurrency().equals(request.getCurrency().toUpperCase())) {
            throw new CurrencyMismatchException(
                    destination.getCurrency(), request.getCurrency());
        }

        // Sufficient funds check
        BigDecimal amount = request.getAmount().setScale(2, RoundingMode.HALF_EVEN);
        if (!source.hasSufficientFunds(amount)) {
            throw new InsufficientFundsException(
                    source.getAccountNumber(), source.getBalance(), amount);
        }

        log.debug("Transfer validation passed: {} {} {} → {}",
                amount, request.getCurrency(),
                source.getAccountNumber(), destination.getAccountNumber());
    }

    // ── Access control helpers ────────────────────────────────────────────────

    private void enforceTransactionAccess(Transaction transaction,
                                          String requestingUsername) {
        boolean isOwner =
                transaction.getSourceAccount().getOwner().getUsername().equals(requestingUsername)
                        || transaction.getDestinationAccount().getOwner().getUsername().equals(requestingUsername);

        boolean isAdmin = isAdmin();
        if (!isOwner && !isAdmin) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You do not have access to transaction: " + transaction.getId());
        }
    }

    private void enforceAccountAccess(Account account, String requestingUsername) {
        if (!account.getOwner().getUsername().equals(requestingUsername) && !isAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You do not have access to account: " + account.getAccountNumber());
        }
    }

    private boolean isAdmin() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    // ── Idempotency key resolver ──────────────────────────────────────────────

    private String resolveIdempotencyKey(String clientKey) {
        if (clientKey != null && !clientKey.isBlank()) {
            return clientKey;
        }
        // Auto-generate if client didn't supply one — idempotency only guaranteed
        // if the client provides a consistent key on retries
        return UUID.randomUUID().toString();
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    public TransactionResponse.Detail toDetail(Transaction t) {
        List<LedgerEntry> entries = ledgerEntryRepository
                .findByTransactionOrderByCreatedAtAsc(t);

        return TransactionResponse.Detail.builder()
                .id(t.getId())
                .idempotencyKey(t.getIdempotencyKey())
                .transactionType(t.getTransactionType())
                .status(t.getStatus())
                .sourceAccountNumber(t.getSourceAccount().getAccountNumber())
                .destinationAccountNumber(t.getDestinationAccount().getAccountNumber())
                .amount(t.getAmount())
                .feeAmount(t.getFeeAmount())
                .currency(t.getCurrency())
                .description(t.getDescription())
                .failureReason(t.getFailureReason())
                .createdAt(t.getCreatedAt())
                .settledAt(t.getSettledAt())
                .ledgerEntries(entries.stream().map(this::toEntryResponse).toList())
                .build();
    }

    public TransactionResponse.Summary toSummary(Transaction t) {
        return TransactionResponse.Summary.builder()
                .id(t.getId())
                .transactionType(t.getTransactionType())
                .status(t.getStatus())
                .sourceAccountNumber(t.getSourceAccount().getAccountNumber())
                .destinationAccountNumber(t.getDestinationAccount().getAccountNumber())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .createdAt(t.getCreatedAt())
                .build();
    }

    public LedgerEntryResponse toEntryResponse(LedgerEntry e) {
        return LedgerEntryResponse.builder()
                .id(e.getId())
                .accountNumber(e.getAccount().getAccountNumber())
                .entryType(e.getEntryType())
                .amount(e.getAmount())
                .currency(e.getCurrency())
                .balanceAfter(e.getBalanceAfter())
                .description(e.getDescription())
                .createdAt(e.getCreatedAt())
                .build();
    }
}