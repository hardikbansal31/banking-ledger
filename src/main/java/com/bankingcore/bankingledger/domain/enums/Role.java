package com.bankingcore.bankingledger.domain.enums;

/**
 * RBAC roles used throughout the application.
 *
 * Spring Security expects role names prefixed with "ROLE_" internally,
 * but we store the bare name (ADMIN, USER) in the database and JWT.
 * The SecurityConfig uses hasRole("ADMIN") which automatically prepends
 * the prefix — so never store "ROLE_ADMIN" in the DB.
 *
 * ADMIN : full access — user management, account operations, reports
 * USER  : self-service — own accounts and transactions only
 */
public enum Role {
    ADMIN,
    USER
}