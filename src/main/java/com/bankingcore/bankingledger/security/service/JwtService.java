package com.bankingcore.bankingledger.security.service;

import com.bankingcore.bankingledger.config.JwtProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JwtService — creates, parses, and validates JWT tokens.
 *
 * Token anatomy:
 *   Header  : {"alg":"HS256","typ":"JWT"}
 *   Payload : {"sub":"username", "role":"USER", "iat":..., "exp":...}
 *   Signature: HMACSHA256(base64(header) + "." + base64(payload), secret)
 *
 * Why HS256 and not RS256?
 *   HS256 (symmetric) is correct for a monolith or single-service JWT issuer.
 *   RS256 (asymmetric) is better when multiple services verify tokens without
 *   sharing the secret — relevant in Phase 6 if you add a separate resource server.
 *
 * This service is stateless — no DB calls, no cache — deliberately so.
 * Token revocation (logout) is handled via a Redis blacklist in Phase 4.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    // ── Token Generation ──────────────────────────────────────────────────────

    /**
     * Creates a signed access token for the given user.
     * Embeds username (subject) and role as a custom claim.
     */
    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> extraClaims = new HashMap<>();
        // Store the role string so the filter can reconstruct authorities
        // without a DB call on every request
        extraClaims.put("role", userDetails.getAuthorities()
                .stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("USER"));
        return buildToken(extraClaims, userDetails, jwtProperties.getExpirationMs());
    }

    /**
     * Creates a refresh token — longer-lived, no role claim.
     * Used by /auth/refresh to issue a new access token without re-login.
     */
    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails, jwtProperties.getRefreshExpirationMs());
    }

    // ── Token Validation ─────────────────────────────────────────────────────

    /**
     * Returns true if the token is well-formed, signed with our secret,
     * not expired, and the subject matches the provided UserDetails.
     * All validation exceptions are caught and logged — never rethrown —
     * so a bad token always results in a clean 401, never a 500.
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired for user '{}': {}", extractUsernameUnchecked(token), e.getMessage());
        } catch (SignatureException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    // ── Claim Extraction ─────────────────────────────────────────────────────

    /** Extracts the 'sub' (username) claim. Throws if token is invalid. */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /** Extracts a single typed claim using a resolver function. */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private String buildToken(Map<String, Object> extraClaims,
                              UserDetails userDetails,
                              long expirationMs) {
        long nowMs = System.currentTimeMillis();
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date(nowMs))
                .expiration(new Date(nowMs + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    /**
     * Best-effort username extraction for logging — returns "unknown" if
     * the token cannot be parsed (e.g. completely malformed).
     */
    private String extractUsernameUnchecked(String token) {
        try {
            return extractUsername(token);
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Derives the HMAC signing key from the Base64-encoded secret in config.
     * Called on every token operation — lightweight, no I/O.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        return Keys.hmacShaKeyFor(keyBytes);
    }
}