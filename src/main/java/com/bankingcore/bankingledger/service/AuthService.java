package com.bankingcore.bankingledger.service;

import com.bankingcore.bankingledger.config.JwtProperties;
import com.bankingcore.bankingledger.service.TokenBlacklistService;
import com.bankingcore.bankingledger.domain.entity.User;
import com.bankingcore.bankingledger.domain.enums.Role;
import com.bankingcore.bankingledger.domain.repository.UserRepository;
import com.bankingcore.bankingledger.dto.request.AuthRequest;
import com.bankingcore.bankingledger.dto.response.AuthResponse;
import com.bankingcore.bankingledger.exception.DuplicateTransactionException;
import com.bankingcore.bankingledger.exception.ResourceNotFoundException;
import com.bankingcore.bankingledger.security.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AuthService — handles all authentication operations.
 *
 * Register flow:
 *   1. Validate username + email uniqueness
 *   2. Hash password with BCrypt
 *   3. Persist User entity
 *   4. Issue access + refresh token pair
 *
 * Login flow:
 *   1. Delegate to AuthenticationManager (loads user, checks BCrypt hash,
 *      checks enabled/locked flags)
 *   2. Issue token pair
 *
 * Refresh flow:
 *   1. Extract username from refresh token (validates signature + expiry)
 *   2. Load UserDetails
 *   3. Issue new access token (refresh token reuse — rotate in Phase 4)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository       userRepository;
    private final PasswordEncoder      passwordEncoder;
    private final JwtService           jwtService;
    private final JwtProperties        jwtProperties;
    private final AuthenticationManager authenticationManager;
    private final TokenBlacklistService tokenBlacklistService;

    // ── Register ──────────────────────────────────────────────────────────────

    @Transactional
    public AuthResponse.TokenPair register(AuthRequest.Register request) {
        log.info("Registration attempt for username='{}' email='{}'",
                request.getUsername(), request.getEmail());

        // Uniqueness checks — report the specific conflict so the client can inform the user
        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("Registration rejected — username '{}' already exists", request.getUsername());
            // Reusing DuplicateTransactionException as a general conflict signal;
            // a dedicated UsernameConflictException can be added if desired
            throw new DuplicateTransactionException("username:" + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration rejected — email '{}' already registered", request.getEmail());
            throw new DuplicateTransactionException("email:" + request.getEmail());
        }

        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(Role.USER)        // all self-registered users start as USER
                .enabled(true)
                .accountNonLocked(true)
                .build();

        User saved = userRepository.save(user);
        log.info("User registered successfully: id='{}' username='{}'",
                saved.getId(), saved.getUsername());

        return buildTokenPair(saved);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    /**
     * @Transactional(readOnly = true) — login is a read operation.
     * AuthenticationManager loads the user from DB; we only read, never write.
     */
    @Transactional(readOnly = true)
    public AuthResponse.TokenPair login(AuthRequest.Login request) {
        log.info("Login attempt for username='{}'", request.getUsername());

        try {
            // This single call does everything:
            //  1. Loads UserDetails via UserDetailsServiceImpl (DB query)
            //  2. Compares BCrypt hashes
            //  3. Checks isEnabled(), isAccountNonLocked(), isAccountNonExpired()
            //  4. Throws a specific AuthenticationException subclass on any failure
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );
        } catch (AuthenticationException ex) {
            // Log the specific cause for ops visibility; GlobalExceptionHandler
            // converts this to a vague 401 for the client
            log.warn("Login failed for username='{}': {}", request.getUsername(), ex.getMessage());
            throw ex;
        }

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getUsername()));

        log.info("Login successful for username='{}' role='{}'", user.getUsername(), user.getRole());
        return buildTokenPair(user);
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AuthResponse.TokenPair refresh(AuthRequest.Refresh request) {
        log.debug("Token refresh attempt");

        String username;
        try {
            username = jwtService.extractUsername(request.getRefreshToken());
        } catch (Exception ex) {
            log.warn("Refresh token parse failed: {}", ex.getMessage());
            // Re-throw; GlobalExceptionHandler converts to 401
            throw ex;
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("Refresh failed — user '{}' not found", username);
                    return new ResourceNotFoundException("User", username);
                });

        if (!jwtService.isTokenValid(request.getRefreshToken(), user)) {
            log.warn("Refresh token invalid or expired for user='{}'", username);
            throw new io.jsonwebtoken.JwtException("Refresh token is invalid or expired");
        }

        log.info("Token refreshed for username='{}'", username);
        // Issue a fresh access token; keep the same refresh token (rotation in Phase 4)
        String newAccessToken = jwtService.generateAccessToken(user);

        return AuthResponse.TokenPair.builder()
                .accessToken(newAccessToken)
                .refreshToken(request.getRefreshToken())
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getExpirationMs() / 1000)
                .user(toUserSummary(user))
                .build();
    }


    // ── Logout ────────────────────────────────────────────────────────────────

    /**
     * Revokes the provided access token by adding it to the Redis blacklist.
     * The token remains in Redis until its natural expiry, after which Redis
     * auto-deletes it (TTL-based cleanup — no manual purge needed).
     */
    @Transactional(readOnly = true)
    public void logout(String rawToken) {
        try {
            java.util.Date expiresAt = jwtService.extractClaim(rawToken,
                    io.jsonwebtoken.Claims::getExpiration);
            tokenBlacklistService.blacklist(rawToken, expiresAt);
            log.info("User logged out — token blacklisted until expiry");
        } catch (Exception ex) {
            // Token may already be expired — still a successful logout
            log.warn("Logout: could not extract expiry from token (may already be expired): {}",
                    ex.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private AuthResponse.TokenPair buildTokenPair(User user) {
        return AuthResponse.TokenPair.builder()
                .accessToken(jwtService.generateAccessToken(user))
                .refreshToken(jwtService.generateRefreshToken(user))
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getExpirationMs() / 1000)
                .user(toUserSummary(user))
                .build();
    }

    private AuthResponse.UserSummary toUserSummary(User user) {
        return AuthResponse.UserSummary.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }
}