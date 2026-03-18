package com.bankingcore.bankingledger.controller;

import com.bankingcore.bankingledger.dto.request.AccountRequest;
import com.bankingcore.bankingledger.dto.response.AccountResponse;
import com.bankingcore.bankingledger.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * AccountController — REST endpoints for account management.
 *
 * URL design:
 *  POST   /accounts                   → open new account (authenticated user)
 *  GET    /accounts                   → list my accounts
 *  GET    /accounts/{accountNumber}   → get one account
 *  GET    /admin/users/{id}/accounts  → admin: get accounts for any user
 *  POST   /admin/accounts/{n}/freeze  → admin: freeze account
 *  POST   /admin/accounts/{n}/activate → admin: unfreeze
 *  DELETE /admin/accounts/{n}         → admin: close account
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    // ── Authenticated user endpoints ─────────────────────────────────────────

    @PostMapping("/accounts")
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody AccountRequest.Create request,
            @AuthenticationPrincipal UserDetails principal) {

        log.info("POST /accounts type='{}' currency='{}' user='{}'",
                request.getAccountType(), request.getCurrency(), principal.getUsername());
        AccountResponse response = accountService.createAccount(principal.getUsername(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<AccountResponse>> getMyAccounts(
            @AuthenticationPrincipal UserDetails principal) {

        log.debug("GET /accounts user='{}'", principal.getUsername());
        return ResponseEntity.ok(accountService.getMyAccounts(principal.getUsername()));
    }

    @GetMapping("/accounts/{accountNumber}")
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable String accountNumber,
            @AuthenticationPrincipal UserDetails principal) {

        log.debug("GET /accounts/{} user='{}'", accountNumber, principal.getUsername());
        return ResponseEntity.ok(
                accountService.getAccountByNumber(accountNumber, principal.getUsername()));
    }

    // ── Admin endpoints ───────────────────────────────────────────────────────

    @GetMapping("/admin/users/{userId}/accounts")
    public ResponseEntity<List<AccountResponse>> getAccountsByUser(@PathVariable UUID userId) {
        log.info("GET /admin/users/{}/accounts", userId);
        return ResponseEntity.ok(accountService.getAccountsByUserId(userId));
    }

    @PostMapping("/admin/accounts/{accountNumber}/freeze")
    public ResponseEntity<AccountResponse> freezeAccount(
            @PathVariable String accountNumber) {
        log.warn("POST /admin/accounts/{}/freeze", accountNumber);
        return ResponseEntity.ok(accountService.freezeAccount(accountNumber));
    }

    @PostMapping("/admin/accounts/{accountNumber}/activate")
    public ResponseEntity<AccountResponse> activateAccount(
            @PathVariable String accountNumber) {
        log.info("POST /admin/accounts/{}/activate", accountNumber);
        return ResponseEntity.ok(accountService.activateAccount(accountNumber));
    }

    @DeleteMapping("/admin/accounts/{accountNumber}")
    public ResponseEntity<Void> closeAccount(@PathVariable String accountNumber) {
        log.warn("DELETE /admin/accounts/{}", accountNumber);
        accountService.closeAccount(accountNumber);
        return ResponseEntity.noContent().build();
    }
}