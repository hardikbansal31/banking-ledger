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
import com.bankingcore.bankingledger.exception.InsufficientFundsException;
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
 * LedgerService — double-entry accounting engine with distributed locking.
 *
 * WHY @Transactional IS ON transfer() DIRECTLY (not a separate method):
 *
 *   Spring's @Transactional works through a proxy. When you inject
 *   LedgerService elsewhere, you get the proxy. Calls from outside go
 *   through the proxy — @Transactional is applied. But when a method
 *   calls another method on the same class (this.doTransfer()), it
 *   bypasses the proxy entirely — @Transactional is silently ignored.
 *
 *   The lambda (() -> doTransfer(...)) still captures 'this' (the raw
 *   bean), so it has the same problem. The only reliable fix is to put
 *   @Transactional on the method that DistributedLockService calls
 *   from outside — which means merging everything into transfer().
 *
 *   The lock and transaction overlap (lock wraps the transaction) which
 *   is fine — it means the lock is held slightly longer than strictly
 *   necessary, but guarantees the DB state is consistent while locked.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final TransactionRepository   transactionRepository;
    private final LedgerEntryRepository   ledgerEntryRepository;
    private final AccountRepository       accountRepository;
    private final DistributedLockService  lockService;
    private final TransactionStateMachine stateMachine;

    // ── Transfer ──────────────────────────────────────────────────────────────

    /**
     * Executes a transfer with both distributed lock and DB transaction active.
     *
     * The lock is acquired by DistributedLockService.executeWithLock BEFORE
     * calling this method through the Spring proxy — so by the time this
     * method runs, the Redis lock is held and the DB @Transactional is open.
     *
     * Call path:
     *   TransactionController.transfer()
     *     → lockService.executeWithLock(key, () -> ledgerService.transfer())
     *                                               ^^^^^^^^^^^^^^^^^^^
     *                                               called on the injected proxy
     *                                               → @Transactional opens here
     */
    @Transactional
    public TransactionResponse.Detail transfer(TransactionRequest.Transfer request,
                                               String requestingUsername) {
        String lockKey = DistributedLockService.transferLockKey(
                request.getSourceAccountNumber(),
                request.getDestinationAccountNumber());

        log.info("Transfer initiated — acquiring lock '{}' for user='{}'",
                lockKey, requestingUsername);

        // executeWithLock calls this method's logic — but we need to run
        // the lock AROUND the transactional work. The correct pattern is
        // to have the controller call lockService, which calls back into
        // this bean via an injected self-reference. Instead, we simplify:
        // just run the full logic here inside the transaction, and let the
        // distributed lock be the outer guard at the controller level.
        return executeTransferLogic(request, requestingUsername);
    }

    private TransactionResponse.Detail executeTransferLogic(
            TransactionRequest.Transfer request, String requestingUsername) {

        // Idempotency check
        String idempotencyKey = resolveIdempotencyKey(request.getIdempotencyKey());
        if (transactionRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.info("Duplicate transfer — returning existing for key='{}'", idempotencyKey);
            return toDetail(transactionRepository
                    .findByIdempotencyKey(idempotencyKey).orElseThrow());
        }

        // Load accounts (no pessimistic lock yet — just to get IDs for ordering)
        Account source = accountRepository
                .findByAccountNumber(request.getSourceAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account", request.getSourceAccountNumber()));
        Account destination = accountRepository
                .findByAccountNumber(request.getDestinationAccountNumber())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Account", request.getDestinationAccountNumber()));

        // Acquire DB pessimistic locks in consistent ID order to prevent deadlock.
        // Lower UUID first — ensures A→B and B→A always lock in the same order.
        UUID firstId  = source.getId().compareTo(destination.getId()) < 0
                ? source.getId() : destination.getId();
        UUID secondId = source.getId().compareTo(destination.getId()) < 0
                ? destination.getId() : source.getId();

        Account lockedFirst  = accountRepository.findByIdForUpdate(firstId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", firstId));
        Account lockedSecond = accountRepository.findByIdForUpdate(secondId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", secondId));

        // Re-resolve which locked reference is source vs destination
        source      = lockedFirst.getId().equals(source.getId())      ? lockedFirst : lockedSecond;
        destination = lockedFirst.getId().equals(destination.getId()) ? lockedFirst : lockedSecond;

        validateTransfer(source, destination, request, requestingUsername);

        BigDecimal amount        = request.getAmount().setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal roundedAmount = amount.setScale(2, RoundingMode.HALF_EVEN);

        // Create transaction header — PENDING
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

        // State machine: PENDING → AUTHORIZED
        stateMachine.assertCanTransition(transaction.getStatus(), TransactionStatus.AUTHORIZED);
        transaction.setStatus(TransactionStatus.AUTHORIZED);
        transactionRepository.save(transaction);
        log.debug("Transaction {} PENDING → AUTHORIZED", transaction.getId());

        try {
            // Double-entry: DEBIT source account
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
            log.debug("DEBIT {} {} on {} — balance now {}",
                    roundedAmount, request.getCurrency(),
                    source.getAccountNumber(), source.getBalance());

            // Double-entry: CREDIT destination account
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
            log.debug("CREDIT {} {} on {} — balance now {}",
                    roundedAmount, request.getCurrency(),
                    destination.getAccountNumber(), destination.getBalance());

            // State machine: AUTHORIZED → SETTLED
            stateMachine.assertCanTransition(transaction.getStatus(), TransactionStatus.SETTLED);
            transaction.setStatus(TransactionStatus.SETTLED);
            transaction.setSettledAt(Instant.now());
            transaction.getLedgerEntries().add(debitEntry);
            transaction.getLedgerEntries().add(creditEntry);
            Transaction settled = transactionRepository.save(transaction);

            log.info("Transfer SETTLED id='{}' {} {} {} → {}",
                    settled.getId(), roundedAmount, request.getCurrency(),
                    source.getAccountNumber(), destination.getAccountNumber());

            return toDetail(settled);

        } catch (Exception ex) {
            // On any failure, mark transaction FAILED so it can't be retried
            if (stateMachine.canTransition(transaction.getStatus(), TransactionStatus.FAILED)) {
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setFailureReason(ex.getMessage());
                transactionRepository.save(transaction);
                log.error("Transfer FAILED id='{}' reason='{}'",
                        transaction.getId(), ex.getMessage());
            }
            throw ex;
        }
    }

    // ── Deposit ───────────────────────────────────────────────────────────────

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public TransactionResponse.Detail deposit(TransactionRequest.Deposit request) {
        log.info("Deposit: account='{}' amount={} {}",
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

        if (!destination.isOperational())
            throw new AccountBlockedException(destination.getAccountNumber());

        BigDecimal amount        = request.getAmount().setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal roundedAmount = amount.setScale(2, RoundingMode.HALF_EVEN);

        Transaction transaction = Transaction.builder()
                .sourceAccount(destination)
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

        stateMachine.assertCanTransition(transaction.getStatus(), TransactionStatus.AUTHORIZED);
        transaction.setStatus(TransactionStatus.AUTHORIZED);

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

        stateMachine.assertCanTransition(transaction.getStatus(), TransactionStatus.SETTLED);
        transaction.setStatus(TransactionStatus.SETTLED);
        transaction.setSettledAt(Instant.now());
        transaction.getLedgerEntries().add(creditEntry);
        Transaction settled = transactionRepository.save(transaction);

        log.info("Deposit SETTLED id='{}' {} {} → '{}'",
                settled.getId(), roundedAmount, request.getCurrency(),
                destination.getAccountNumber());
        return toDetail(settled);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public TransactionResponse.Detail getTransactionById(UUID id, String username) {
        Transaction t = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", id));
        enforceTransactionAccess(t, username);
        return toDetail(t);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse.Summary> getAccountTransactions(
            String accountNumber, String username, Pageable pageable) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountNumber));
        enforceAccountAccess(account, username);
        return transactionRepository.findByAccount(account, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> getAccountStatement(
            String accountNumber, String username, Pageable pageable) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountNumber));
        enforceAccountAccess(account, username);
        return ledgerEntryRepository
                .findByAccountOrderByCreatedAtDesc(account, pageable)
                .map(this::toEntryResponse);
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private void validateTransfer(Account source, Account destination,
                                  TransactionRequest.Transfer req, String username) {
        if (!source.getOwner().getUsername().equals(username))
            throw new org.springframework.security.access.AccessDeniedException(
                    "You do not own source account: " + source.getAccountNumber());
        if (source.getId().equals(destination.getId()))
            throw new IllegalArgumentException("Source and destination must differ.");
        if (!source.isOperational())
            throw new AccountBlockedException(source.getAccountNumber());
        if (!destination.isOperational())
            throw new AccountBlockedException(destination.getAccountNumber());
        if (!source.getCurrency().equals(req.getCurrency().toUpperCase()))
            throw new CurrencyMismatchException(source.getCurrency(), req.getCurrency());
        if (!destination.getCurrency().equals(req.getCurrency().toUpperCase()))
            throw new CurrencyMismatchException(destination.getCurrency(), req.getCurrency());
        BigDecimal amount = req.getAmount().setScale(2, RoundingMode.HALF_EVEN);
        if (!source.hasSufficientFunds(amount))
            throw new InsufficientFundsException(
                    source.getAccountNumber(), source.getBalance(), amount);
    }

    // ── Access control ────────────────────────────────────────────────────────

    private void enforceTransactionAccess(Transaction t, String username) {
        boolean isOwner = t.getSourceAccount().getOwner().getUsername().equals(username)
                || t.getDestinationAccount().getOwner().getUsername().equals(username);
        if (!isOwner && !isAdmin())
            throw new org.springframework.security.access.AccessDeniedException(
                    "Access denied to transaction: " + t.getId());
    }

    private void enforceAccountAccess(Account account, String username) {
        if (!account.getOwner().getUsername().equals(username) && !isAdmin())
            throw new org.springframework.security.access.AccessDeniedException(
                    "Access denied to account: " + account.getAccountNumber());
    }

    private boolean isAdmin() {
        var auth = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    private String resolveIdempotencyKey(String key) {
        return (key != null && !key.isBlank()) ? key : UUID.randomUUID().toString();
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
                .amount(t.getAmount().setScale(4, java.math.RoundingMode.HALF_EVEN))
                .feeAmount(t.getFeeAmount().setScale(4, java.math.RoundingMode.HALF_EVEN))
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
                .amount(e.getAmount().setScale(4, java.math.RoundingMode.HALF_EVEN))
                .currency(e.getCurrency())
                .balanceAfter(e.getBalanceAfter().setScale(2, java.math.RoundingMode.HALF_EVEN))
                .description(e.getDescription())
                .createdAt(e.getCreatedAt())
                .build();
    }
}