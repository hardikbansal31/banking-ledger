package com.bankingcore.bankingledger.domain.entity;

import com.bankingcore.bankingledger.domain.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * User entity — persisted principal for authentication and authorisation.
 *
 * Implements {@link UserDetails} directly so Spring Security can use it
 * without a separate adapter/wrapper class. This is a clean pattern for
 * smaller services; for microservices with complex identity you'd separate
 * the UserDetails adapter.
 *
 * Table: users
 * Inherits: id, version, createdAt, updatedAt, createdBy, updatedBy,
 *           deleted, deletedAt  (all from BaseEntity)
 *
 * @SQLRestriction ensures every JPQL/HQL query automatically appends
 * "AND deleted = false" — soft-deleted users are invisible to the app.
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_users_email",    columnNames = "email"),
                @UniqueConstraint(name = "uq_users_username", columnNames = "username")
        }
)
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity implements UserDetails {

    // ── Identity ─────────────────────────────────────────────────────────────

    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    /**
     * BCrypt-hashed password. Never store or log plaintext.
     * Column length 72 = max meaningful BCrypt input length.
     */
    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    // ── Profile ──────────────────────────────────────────────────────────────

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    // ── RBAC ─────────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    // ── Account status flags ─────────────────────────────────────────────────

    /**
     * False when email verification or admin review is pending.
     * Maps to UserDetails.isEnabled().
     */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /**
     * True after too many failed login attempts (brute-force protection).
     * Set by a LoginAttemptService (Phase 6 enhancement).
     */
    @Column(name = "account_non_locked", nullable = false)
    @Builder.Default
    private boolean accountNonLocked = true;

    // ── UserDetails contract ──────────────────────────────────────────────────

    /**
     * Returns a single GrantedAuthority: "ROLE_ADMIN" or "ROLE_USER".
     * Spring Security's hasRole("ADMIN") matcher strips the prefix for you.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /** UserDetails uses getPassword(), not getPasswordHash(). */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isAccountNonExpired()  { return true; }

    @Override
    public boolean isAccountNonLocked()   { return accountNonLocked; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled()            { return enabled; }
}