package com.bankingcore.bankingledger.service;

import com.bankingcore.bankingledger.domain.entity.User;
import com.bankingcore.bankingledger.domain.enums.Role;
import com.bankingcore.bankingledger.domain.repository.UserRepository;
import com.bankingcore.bankingledger.dto.response.AuthResponse;
import com.bankingcore.bankingledger.dto.response.UserResponse;
import com.bankingcore.bankingledger.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * UserService — user profile reads and admin-level user management.
 *
 * @PreAuthorize annotations enforce RBAC at the method level (enabled by
 * @EnableMethodSecurity in SecurityConfig). This means even if a URL rule
 * were misconfigured, the method-level check is a second line of defence.
 *
 * Return types:
 *   UserResponse.Detail  — full fields including audit + status flags
 *                          returned to the authenticated user and admins
 *   AuthResponse.UserSummary — compact form used inside login TokenPair
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // ── Read operations ───────────────────────────────────────────────────────

    /**
     * Returns the currently authenticated user's own full profile.
     * Any authenticated user can call this — no role restriction.
     */
    @Transactional(readOnly = true)
    public UserResponse.Detail getMyProfile(String username) {
        log.debug("Fetching profile for username='{}'", username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.error("Authenticated user '{}' not found in DB — data inconsistency", username);
                    return new UsernameNotFoundException("User not found: " + username);
                });

        return toDetail(user);
    }

    /**
     * Admin: fetch any user by UUID — returns full Detail including status flags.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public UserResponse.Detail getUserById(UUID userId) {
        log.debug("Admin fetching user id='{}'", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        return toDetail(user);
    }

    /**
     * Admin: list all non-deleted users as Detail objects.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public List<UserResponse.Detail> getAllUsers() {
        log.debug("Admin listing all users");
        return userRepository.findAll()
                .stream()
                .map(this::toDetail)
                .toList();
    }

    // ── Write operations (ADMIN only) ─────────────────────────────────────────

    /**
     * Admin: promote a user to ADMIN role.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse.Detail promoteToAdmin(UUID userId) {
        log.warn("ADMIN PROMOTION: user id='{}' being promoted to ADMIN", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Role previous = user.getRole();
        user.setRole(Role.ADMIN);
        User saved = userRepository.save(user);

        log.info("User '{}' promoted from {} to ADMIN", saved.getUsername(), previous);
        return toDetail(saved);
    }

    /**
     * Admin: lock a user account (e.g. after fraud detection).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public UserResponse.Detail lockUser(UUID userId) {
        log.warn("ACCOUNT LOCK: locking user id='{}'", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        user.setAccountNonLocked(false);
        User saved = userRepository.save(user);

        log.info("User '{}' account locked", saved.getUsername());
        return toDetail(saved);
    }

    /**
     * Admin: soft-delete a user (sets deleted = true, never issues DELETE SQL).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void softDeleteUser(UUID userId) {
        log.warn("SOFT DELETE: deleting user id='{}'", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        user.setDeleted(true);
        user.setDeletedAt(Instant.now());
        user.setEnabled(false);
        userRepository.save(user);

        log.info("User '{}' soft-deleted", user.getUsername());
    }

    // ── Mapper: Entity → DTO ──────────────────────────────────────────────────

    /**
     * Maps a User entity to the full Detail DTO.
     * Called for both /users/me (self) and /admin/users/** (admin).
     */
    public UserResponse.Detail toDetail(User user) {
        return UserResponse.Detail.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole())
                .enabled(user.isEnabled())
                .accountNonLocked(user.isAccountNonLocked())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .createdBy(user.getCreatedBy())
                .build();
    }

    /**
     * Maps a User entity to the compact Summary used inside auth token responses.
     * Called by AuthService when building the TokenPair.
     */
    public AuthResponse.UserSummary toSummary(User user) {
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