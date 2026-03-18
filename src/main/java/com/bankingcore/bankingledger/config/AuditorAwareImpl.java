package com.bankingcore.bankingledger.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * AuditorAwareImpl — tells Spring Data JPA who is performing the current write.
 *
 * Spring Data calls getCurrentAuditor() every time an entity with @CreatedBy
 * or @LastModifiedBy is saved. The returned string is stored in created_by /
 * updated_by columns — completing the audit trail in BaseEntity.
 *
 * The @EnableJpaAuditing annotation that activates this bean is in JpaConfig.
 */
@Slf4j
@Component
public class AuditorAwareImpl implements AuditorAware<String> {

    private static final String SYSTEM_USER = "SYSTEM";

    /**
     * Returns the authenticated username, or "SYSTEM" for:
     *  - Background jobs (Quartz, scheduled tasks)
     *  - Application startup operations
     *  - Any context where no user is authenticated
     *
     * "anonymousUser" is Spring Security's placeholder before authentication
     * and should map to SYSTEM rather than being stored literally.
     */
    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            log.trace("No authenticated principal — using '{}' as auditor", SYSTEM_USER);
            return Optional.of(SYSTEM_USER);
        }

        String username = auth.getName();
        log.trace("Current auditor: '{}'", username);
        return Optional.of(username);
    }
}