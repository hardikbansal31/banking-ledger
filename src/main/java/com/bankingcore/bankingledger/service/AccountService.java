package com.bankingcore.bankingledger.service;

import com.bankingcore.bankingledger.domain.entity.Account;
import com.bankingcore.bankingledger.domain.entity.User;
import com.bankingcore.bankingledger.domain.enums.AccountStatus;
import com.bankingcore.bankingledger.domain.repository.AccountRepository;
import com.bankingcore.bankingledger.domain.repository.UserRepository;
import com.bankingcore.bankingledger.dto.request.AccountRequest;
import com.bankingcore.bankingledger.dto.response.AccountResponse;
import com.bankingcore.bankingledger.exception.AccountBlockedException;
import com.bankingcore.bankingledger.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AccountService — manages the full account lifecycle.
 *
 * Account number format: ACC-<zero-padded 6-digit sequence>
 * e.g. ACC-000001, ACC-000042
 * Generated in Java (not DB) for portability across DB engines.
 *
 * Currency validation: uses java.util.Currency which knows all ISO 4217 codes.
 * No hardcoded whitelist needed — if it's a real ISO currency, it's accepted.
 * Extend to a whitelist if you need to restrict to specific currencies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository    userRepository;

    /**
     * In-memory counter for account number generation.
     * In production: replace with a DB sequence or Redis INCR for cluster safety.
     * Phase 4 Redisson integration will make this distributed.
     */
    private final AtomicLong accountSequence = new AtomicLong(0);

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public AccountResponse createAccount(String ownerUsername, AccountRequest.Create request) {
        log.info("Creating {} account in {} for user='{}'",
                request.getAccountType(), request.getCurrency(), ownerUsername);

        // Validate ISO 4217 currency code
        validateCurrency(request.getCurrency());

        User owner = userRepository.findByUsername(ownerUsername)
                .orElseThrow(() -> {
                    log.error("Account creation failed — owner '{}' not found", ownerUsername);
                    return new UsernameNotFoundException("User not found: " + ownerUsername);
                });

        String accountNumber = generateAccountNumber();

        BigDecimal initialBalance = request.getInitialDeposit() != null
                ? request.getInitialDeposit().setScale(2, RoundingMode.HALF_EVEN)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN);

        Account account = Account.builder()
                .owner(owner)
                .accountNumber(accountNumber)
                .accountType(request.getAccountType())
                .currency(request.getCurrency().toUpperCase())
                .balance(initialBalance)
                .minimumBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_EVEN))
                .status(AccountStatus.ACTIVE)   // skip PENDING_VERIFICATION for now; add KYC in Phase 6
                .build();

        Account saved = accountRepository.save(account);

        log.info("Account created: number='{}' type='{}' currency='{}' owner='{}'",
                saved.getAccountNumber(), saved.getAccountType(),
                saved.getCurrency(), ownerUsername);

        return toResponse(saved);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Returns all accounts owned by the requesting user.
     * A USER can only see their own accounts.
     * An ADMIN can call getAccountsByUsername() to see any user's accounts.
     */
    @Transactional(readOnly = true)
    public List<AccountResponse> getMyAccounts(String ownerUsername) {
        log.debug("Fetching accounts for user='{}'", ownerUsername);

        User owner = userRepository.findByUsername(ownerUsername)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + ownerUsername));

        return accountRepository.findAllByOwner(owner)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountByNumber(String accountNumber, String requestingUsername) {
        log.debug("Fetching account='{}' for user='{}'", accountNumber, requestingUsername);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountNumber));

        enforceOwnershipOrAdmin(account, requestingUsername);
        return toResponse(account);
    }

    // ── Admin operations ──────────────────────────────────────────────────────

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByUserId(UUID userId) {
        log.debug("Admin fetching accounts for userId='{}'", userId);

        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        return accountRepository.findAllByOwner(owner)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AccountResponse freezeAccount(String accountNumber) {
        log.warn("FREEZE: freezing account='{}'", accountNumber);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountNumber));

        account.setStatus(AccountStatus.FROZEN);
        Account saved = accountRepository.save(account);

        log.warn("Account '{}' frozen by admin", accountNumber);
        return toResponse(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AccountResponse activateAccount(String accountNumber) {
        log.info("ACTIVATE: activating account='{}'", accountNumber);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountNumber));

        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new AccountBlockedException(accountNumber);
        }

        account.setStatus(AccountStatus.ACTIVE);
        Account saved = accountRepository.save(account);

        log.info("Account '{}' activated", accountNumber);
        return toResponse(saved);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void closeAccount(String accountNumber) {
        log.warn("CLOSE: closing account='{}'", accountNumber);

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountNumber));

        account.setStatus(AccountStatus.CLOSED);
        account.setDeleted(true);
        account.setDeletedAt(Instant.now());
        accountRepository.save(account);

        log.warn("Account '{}' closed and soft-deleted", accountNumber);
    }

    // ── Internal helpers (called by LedgerService in Phase 3) ────────────────

    /**
     * Loads an account with a pessimistic write lock.
     * Package-visible — only used by LedgerService in the same transaction.
     */
    @Transactional
    public Account loadForUpdate(UUID accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /**
     * Throws if the requesting user is neither the account owner nor an ADMIN.
     * This enforces data isolation between users at the service layer.
     */
    private void enforceOwnershipOrAdmin(Account account, String requestingUsername) {
        boolean isOwner = account.getOwner().getUsername().equals(requestingUsername);
        // isAdmin check via Spring Security context
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isOwner && !isAdmin) {
            log.warn("User '{}' attempted to access account '{}' owned by '{}'",
                    requestingUsername,
                    account.getAccountNumber(),
                    account.getOwner().getUsername());
            throw new org.springframework.security.access.AccessDeniedException(
                    "You do not have access to account: " + account.getAccountNumber());
        }
    }

    /**
     * Validates the currency string against the JDK's ISO 4217 currency set.
     * Throws IllegalArgumentException (→ 400) for unknown codes.
     */
    private void validateCurrency(String currencyCode) {
        Set<Currency> available = Currency.getAvailableCurrencies();
        boolean valid = available.stream()
                .anyMatch(c -> c.getCurrencyCode().equals(currencyCode.toUpperCase()));
        if (!valid) {
            log.warn("Invalid currency code submitted: '{}'", currencyCode);
            throw new IllegalArgumentException(
                    "Unknown currency code: '" + currencyCode + "'. Use ISO 4217 (e.g. USD, EUR, INR).");
        }
    }

    /**
     * Generates the next account number.
     * Example: ACC-000001
     * Thread-safe via AtomicLong; will be replaced with Redis INCR in Phase 4.
     */
    private String generateAccountNumber() {
        // Initialise from DB count on first call (survives restarts)
        if (accountSequence.get() == 0) {
            long count = accountRepository.count();
            accountSequence.set(count);
        }
        long next = accountSequence.incrementAndGet();
        String number = "ACC-%06d".formatted(next);
        // Retry if (extremely unlikely) collision occurs
        while (accountRepository.existsByAccountNumber(number)) {
            next = accountSequence.incrementAndGet();
            number = "ACC-%06d".formatted(next);
        }
        return number;
    }

    // ── Entity → DTO mapper ───────────────────────────────────────────────────

    public AccountResponse toResponse(Account account) {
        return AccountResponse.builder()
                .id(account.getId())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType())
                .status(account.getStatus())
                .currency(account.getCurrency())
                .balance(account.getBalance())
                .minimumBalance(account.getMinimumBalance())
                .ownerId(account.getOwner().getId())
                .ownerUsername(account.getOwner().getUsername())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}