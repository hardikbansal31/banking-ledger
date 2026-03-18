package com.bankingcore.bankingledger.domain.repository;

import com.bankingcore.bankingledger.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * UserRepository — data access for the users table.
 *
 * Spring Data JPA generates the SQL at startup — no implementation needed.
 * The @SQLRestriction on User means every query here automatically filters
 * deleted = false, so soft-deleted users are never returned.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Used by UserDetailsService to look up a user at login time.
     * Also used to check for duplicate usernames at registration.
     */
    Optional<User> findByUsername(String username);

    /**
     * Used at registration to prevent duplicate email addresses.
     */
    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}