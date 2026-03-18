package com.bankingcore.bankingledger.security.service;

import com.bankingcore.bankingledger.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UserDetailsServiceImpl — the bridge between Spring Security and the database.
 *
 * Spring Security calls loadUserByUsername() at two points:
 *  1. During form/basic login (not used here — we use JWT)
 *  2. Inside JwtAuthenticationFilter when it needs to verify token ownership
 *
 * The @Transactional(readOnly = true) annotation:
 *  - Opens a read-only Hibernate session (no dirty-checking overhead)
 *  - Signals to the DB that no writes will occur (allows read replicas)
 *  - Never use a writable transaction just to load a user
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    /**
     * Loads a user by username for Spring Security.
     *
     * Note: we deliberately do NOT distinguish between "user not found" and
     * "wrong password" in the error message. Both return the same 401 from
     * the GlobalExceptionHandler. This prevents username enumeration attacks.
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Loading user by username: '{}'", username);

        return userRepository.findByUsername(username)
                .orElseThrow(() -> {
                    // Log at WARN — repeated failures may indicate an attack
                    log.warn("Authentication attempt for unknown username: '{}'", username);
                    // Throw generic message — do NOT say "user not found"
                    return new UsernameNotFoundException("Authentication failed");
                });
    }
}