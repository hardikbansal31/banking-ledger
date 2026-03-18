package com.bankingcore.bankingledger.controller;

import com.bankingcore.bankingledger.dto.request.AuthRequest;
import com.bankingcore.bankingledger.dto.response.AuthResponse;
import com.bankingcore.bankingledger.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController — handles authentication endpoints.
 *
 * All endpoints here are permitAll() in SecurityConfig — no JWT required.
 *
 * REST conventions used:
 *  POST /auth/register → 201 Created  (new resource created)
 *  POST /auth/login    → 200 OK       (returning existing resource state)
 *  POST /auth/refresh  → 200 OK       (exchange, not creation)
 *
 * @Valid triggers Bean Validation on the request body.
 * Validation failures throw MethodArgumentNotValidException →
 * caught by GlobalExceptionHandler → 422 with field errors.
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse.TokenPair> register(
            @Valid @RequestBody AuthRequest.Register request) {

        log.info("POST /auth/register for username='{}'", request.getUsername());
        AuthResponse.TokenPair response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse.TokenPair> login(
            @Valid @RequestBody AuthRequest.Login request) {

        log.info("POST /auth/login for username='{}'", request.getUsername());
        AuthResponse.TokenPair response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse.TokenPair> refresh(
            @Valid @RequestBody AuthRequest.Refresh request) {

        log.debug("POST /auth/refresh");
        AuthResponse.TokenPair response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }
}