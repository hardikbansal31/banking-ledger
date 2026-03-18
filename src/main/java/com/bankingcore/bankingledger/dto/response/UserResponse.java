package com.bankingcore.bankingledger.dto.response;

import com.bankingcore.bankingledger.domain.enums.Role;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * UserResponse — detailed user representation for API responses.
 *
 * Two variants are provided:
 *
 *  Detail  : full fields including audit metadata — returned to ADMIN callers
 *            and the user reading their own profile (/users/me)
 *
 *  Summary : compact — used inside TokenPair at login, or when embedding
 *            user info inside another resource (e.g. an AccountResponse)
 *            AuthResponse.UserSummary already exists for the auth flow;
 *            this class is for non-auth contexts where richer data is needed.
 *
 * Why not just one DTO?
 *   Different API surfaces have different security requirements.
 *   A summary embedded in a transaction response should never leak
 *   audit timestamps or account lock status to the wrong caller.
 *   Keeping them separate makes the security contract explicit.
 */
public class UserResponse {

    /**
     * Full user detail — returned from GET /users/me and GET /admin/users/{id}.
     * Includes account status flags so an admin can see lock/enabled state.
     * Never includes passwordHash.
     */
    @Data
    @Builder
    public static class Detail {

        private UUID    id;
        private String  username;
        private String  email;
        private String  firstName;
        private String  lastName;
        private Role    role;

        // Account status — useful for admin dashboards
        private boolean enabled;
        private boolean accountNonLocked;

        // Audit fields from BaseEntity
        private Instant createdAt;
        private Instant updatedAt;
        private String  createdBy;
    }

    /**
     * Compact user reference — safe to embed inside other response objects
     * (e.g. inside AccountResponse when an admin needs to know the owner).
     * Contains no status flags or audit timestamps.
     */
    @Data
    @Builder
    public static class Compact {

        private UUID   id;
        private String username;
        private String firstName;
        private String lastName;
        private Role   role;
    }
}