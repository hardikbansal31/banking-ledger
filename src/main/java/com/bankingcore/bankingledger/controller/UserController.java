package com.bankingcore.bankingledger.controller;

import com.bankingcore.bankingledger.dto.response.UserResponse;
import com.bankingcore.bankingledger.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * UserController — user profile reads and admin user management.
 *
 * Return types:
 *   GET /users/me            → UserResponse.Detail  (self — full profile)
 *   GET /admin/users         → List<UserResponse.Detail>
 *   GET /admin/users/{id}    → UserResponse.Detail
 *   POST /admin/users/{id}/promote  → UserResponse.Detail
 *   POST /admin/users/{id}/lock     → UserResponse.Detail
 *   DELETE /admin/users/{id}        → 204 No Content
 *
 * RBAC is enforced at two levels:
 *   1. URL level in SecurityConfig  (/admin/** requires ADMIN)
 *   2. Method level @PreAuthorize in UserService (double defence)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // ── Authenticated user ────────────────────────────────────────────────────

    /** Any authenticated user reads their own full profile. */
    @GetMapping("/users/me")
    public ResponseEntity<UserResponse.Detail> getMyProfile(
            @AuthenticationPrincipal UserDetails principal) {

        log.debug("GET /users/me user='{}'", principal.getUsername());
        return ResponseEntity.ok(userService.getMyProfile(principal.getUsername()));
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @GetMapping("/admin/users")
    public ResponseEntity<List<UserResponse.Detail>> getAllUsers() {
        log.info("GET /admin/users");
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/admin/users/{id}")
    public ResponseEntity<UserResponse.Detail> getUserById(@PathVariable UUID id) {
        log.info("GET /admin/users/{}", id);
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PostMapping("/admin/users/{id}/promote")
    public ResponseEntity<UserResponse.Detail> promoteToAdmin(@PathVariable UUID id) {
        log.warn("POST /admin/users/{}/promote", id);
        return ResponseEntity.ok(userService.promoteToAdmin(id));
    }

    @PostMapping("/admin/users/{id}/lock")
    public ResponseEntity<UserResponse.Detail> lockUser(@PathVariable UUID id) {
        log.warn("POST /admin/users/{}/lock", id);
        return ResponseEntity.ok(userService.lockUser(id));
    }

    @DeleteMapping("/admin/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID id) {
        log.warn("DELETE /admin/users/{}", id);
        userService.softDeleteUser(id);
        return ResponseEntity.noContent().build();
    }
}