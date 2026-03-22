package com.bankingcore.bankingledger.controller;

import com.bankingcore.bankingledger.dto.request.AuthRequest;
import com.bankingcore.bankingledger.dto.response.AuthResponse;
import com.bankingcore.bankingledger.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * AuthController — authentication endpoints.
 *
 * POST /auth/register → 201 Created
 * POST /auth/login    → 200 OK
 * POST /auth/refresh  → 200 OK
 * POST /auth/logout   → 204 No Content  (blacklists the token in Redis)
 *
 * register/login/refresh are permitAll() in SecurityConfig.
 * logout requires a valid token — you must be authenticated to log out.
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

        log.info("POST /auth/register username='{}'", request.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse.TokenPair> login(
            @Valid @RequestBody AuthRequest.Login request) {

        log.info("POST /auth/login username='{}'", request.getUsername());
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse.TokenPair> refresh(
            @Valid @RequestBody AuthRequest.Refresh request) {

        log.debug("POST /auth/refresh");
        return ResponseEntity.ok(authService.refresh(request));
    }

    /**
     * Logout — blacklists the current access token in Redis.
     * After this call, the token is rejected by JwtAuthenticationFilter
     * even if it hasn't expired yet.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authHeader,
            @AuthenticationPrincipal UserDetails principal) {

        log.info("POST /auth/logout username='{}'", principal.getUsername());
        String token = authHeader.startsWith("Bearer ")
                ? authHeader.substring(7)
                : authHeader;
        authService.logout(token);
        return ResponseEntity.noContent().build();
    }
}