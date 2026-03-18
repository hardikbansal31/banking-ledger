package com.bankingcore.bankingledger.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * DTOs for authentication endpoints.
 *
 * Why separate DTOs instead of using the entity directly?
 *  - Entities can have fields you never want to expose (passwordHash, version)
 *  - DTOs are validated at the HTTP boundary; entities are validated at the DB boundary
 *  - Changing the API shape doesn't force a DB schema change and vice versa
 *  - This separation is called the "Anti-Corruption Layer" pattern
 */
public class AuthRequest {

    /** POST /auth/register */
    @Data
    public static class Register {

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
        @Pattern(regexp = "^[a-zA-Z0-9_.-]+$",
                message = "Username may only contain letters, digits, underscores, dots, hyphens")
        private String username;

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be a valid address")
        @Size(max = 150, message = "Email must not exceed 150 characters")
        private String email;

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        // Basic complexity — strengthen in production with a custom validator
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password must contain at least one uppercase letter, one lowercase letter, and one digit")
        private String password;

        @NotBlank(message = "First name is required")
        @Size(max = 80)
        private String firstName;

        @NotBlank(message = "Last name is required")
        @Size(max = 80)
        private String lastName;
    }

    /** POST /auth/login */
    @Data
    public static class Login {

        @NotBlank(message = "Username is required")
        private String username;

        @NotBlank(message = "Password is required")
        private String password;
    }

    /** POST /auth/refresh */
    @Data
    public static class Refresh {

        @NotBlank(message = "Refresh token is required")
        private String refreshToken;
    }
}