package com.bankingcore.bankingledger.controller;

import com.bankingcore.bankingledger.dto.request.TransactionRequest;
import com.bankingcore.bankingledger.dto.response.LedgerEntryResponse;
import com.bankingcore.bankingledger.dto.response.TransactionResponse;
import com.bankingcore.bankingledger.service.DistributedLockService;
import com.bankingcore.bankingledger.service.LedgerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * TransactionController — REST endpoints for the double-entry ledger.
 *
 * WHY THE LOCK IS ACQUIRED HERE (not in LedgerService):
 *
 *   Spring @Transactional works via proxy. When code inside LedgerService
 *   calls another method on the same class (this.xyz()), it bypasses the
 *   proxy — @Transactional is silently ignored.
 *
 *   The solution: the controller acquires the distributed lock, then calls
 *   ledgerService.transfer() through the injected proxy. The proxy sees
 *   the @Transactional annotation and opens a DB transaction. The full
 *   order is:
 *
 *     Controller → lockService.executeWithLock(key,
 *                     () -> ledgerService.transfer()  ← called on proxy ✓
 *                  )
 *
 *   This ensures:
 *     1. Redis lock is held       (DistributedLockService)
 *     2. DB transaction is open   (@Transactional via proxy)
 *     3. DB pessimistic lock held (SELECT FOR UPDATE inside transfer())
 *   All three guards are active simultaneously for the critical section.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class TransactionController {

    private final LedgerService          ledgerService;
    private final DistributedLockService lockService;

    // ── Transfer ──────────────────────────────────────────────────────────────

    @PostMapping("/transactions/transfer")
    public ResponseEntity<TransactionResponse.Detail> transfer(
            @Valid @RequestBody TransactionRequest.Transfer request,
            @AuthenticationPrincipal UserDetails principal) {

        log.info("POST /transactions/transfer user='{}'", principal.getUsername());

        // Acquire distributed lock HERE — outside LedgerService —
        // so that ledgerService.transfer() is called through the Spring
        // proxy and @Transactional takes effect correctly.
        String lockKey = DistributedLockService.transferLockKey(
                request.getSourceAccountNumber(),
                request.getDestinationAccountNumber());

        TransactionResponse.Detail response = lockService.executeWithLock(lockKey,
                () -> ledgerService.transfer(request, principal.getUsername()));

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── Deposit (Admin only) ──────────────────────────────────────────────────

    @PostMapping("/admin/transactions/deposit")
    public ResponseEntity<TransactionResponse.Detail> deposit(
            @Valid @RequestBody TransactionRequest.Deposit request) {

        log.info("POST /admin/transactions/deposit account='{}'",
                request.getAccountNumber());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ledgerService.deposit(request));
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @GetMapping("/transactions/{id}")
    public ResponseEntity<TransactionResponse.Detail> getTransaction(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal) {

        log.debug("GET /transactions/{} user='{}'", id, principal.getUsername());
        return ResponseEntity.ok(
                ledgerService.getTransactionById(id, principal.getUsername()));
    }

    @GetMapping("/accounts/{accountNumber}/transactions")
    public ResponseEntity<Page<TransactionResponse.Summary>> getTransactions(
            @PathVariable String accountNumber,
            @AuthenticationPrincipal UserDetails principal,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {

        log.debug("GET /accounts/{}/transactions user='{}'",
                accountNumber, principal.getUsername());
        return ResponseEntity.ok(
                ledgerService.getAccountTransactions(
                        accountNumber, principal.getUsername(), pageable));
    }

    @GetMapping("/accounts/{accountNumber}/statement")
    public ResponseEntity<Page<LedgerEntryResponse>> getStatement(
            @PathVariable String accountNumber,
            @AuthenticationPrincipal UserDetails principal,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {

        log.debug("GET /accounts/{}/statement user='{}'",
                accountNumber, principal.getUsername());
        return ResponseEntity.ok(
                ledgerService.getAccountStatement(
                        accountNumber, principal.getUsername(), pageable));
    }
}