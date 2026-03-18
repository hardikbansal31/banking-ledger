package com.bankingcore.bankingledger.dto.response;

import com.bankingcore.bankingledger.domain.enums.Role;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTOs for authentication endpoints.
 * Only fields safe to expose to the client are included.
 */
public class AuthResponse {

    /** Returned from /auth/login and /auth/register */
    @Data
    @Builder
    public static class TokenPair {
        private String accessToken;
        private String refreshToken;
        private String tokenType;       // always "Bearer"
        private long   expiresIn;       // seconds until access token expires
        private UserSummary user;
    }

    /** Embedded user info in login response — avoids a separate /me call */
    @Data
    @Builder
    public static class UserSummary {
        private UUID   id;
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        private Role   role;
        private Instant createdAt;
    }
}