package com.bankingcore.bankingledger.controller;

import com.bankingcore.bankingledger.dto.request.ScheduledPaymentRequest;
import com.bankingcore.bankingledger.dto.response.ScheduledPaymentResponse;
import com.bankingcore.bankingledger.service.ScheduledPaymentService;
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
 * ScheduledPaymentController — manage recurring payment schedules.
 *
 * POST   /scheduled-payments          → create recurring payment
 * GET    /scheduled-payments          → list my scheduled payments
 * DELETE /scheduled-payments/{id}     → cancel a scheduled payment
 */
@Slf4j
@RestController
@RequestMapping("/scheduled-payments")
@RequiredArgsConstructor
public class ScheduledPaymentController {

    private final ScheduledPaymentService scheduledPaymentService;

    @PostMapping
    public ResponseEntity<ScheduledPaymentResponse> create(
            @Valid @RequestBody ScheduledPaymentRequest.Create request,
            @AuthenticationPrincipal UserDetails principal) {

        log.info("POST /scheduled-payments user='{}'", principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scheduledPaymentService.create(request, principal.getUsername()));
    }

    @GetMapping
    public ResponseEntity<List<ScheduledPaymentResponse>> getMyPayments(
            @AuthenticationPrincipal UserDetails principal) {

        log.debug("GET /scheduled-payments user='{}'", principal.getUsername());
        return ResponseEntity.ok(
                scheduledPaymentService.getMyScheduledPayments(principal.getUsername()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ScheduledPaymentResponse> cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails principal) {

        log.info("DELETE /scheduled-payments/{} user='{}'", id, principal.getUsername());
        return ResponseEntity.ok(
                scheduledPaymentService.cancel(id, principal.getUsername()));
    }
}