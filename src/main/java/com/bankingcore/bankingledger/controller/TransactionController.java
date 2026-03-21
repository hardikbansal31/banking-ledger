package com.bankingcore.bankingledger.controller;

import com.bankingcore.bankingledger.dto.request.TransactionRequest;
import com.bankingcore.bankingledger.dto.response.LedgerEntryResponse;
import com.bankingcore.bankingledger.dto.response.TransactionResponse;
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
 * POST /transactions/transfer           → initiate a transfer
 * POST /admin/transactions/deposit      → admin: deposit funds
 * GET  /transactions/{id}               → get one transaction
 * GET  /accounts/{number}/transactions  → paginated transaction list
 * GET  /accounts/{number}/statement     → paginated ledger entries
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class TransactionController {

    private final LedgerService ledgerService;

    // ── Transfer ──────────────────────────────────────────────────────────────

    @PostMapping("/transactions/transfer")
    public ResponseEntity<TransactionResponse.Detail> transfer(
            @Valid @RequestBody TransactionRequest.Transfer request,
            @AuthenticationPrincipal UserDetails principal) {

        log.info("POST /transactions/transfer user='{}'", principal.getUsername());
        TransactionResponse.Detail response =
                ledgerService.transfer(request, principal.getUsername());
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

    /**
     * Paginated transaction list for an account.
     * Default: 20 per page, newest first.
     * Use ?page=1&size=10&sort=createdAt,desc to customise.
     */
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

    /**
     * Paginated ledger statement — raw DEBIT/CREDIT entries for an account.
     * This is what powers an account statement or mini-statement feature.
     */
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