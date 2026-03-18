package com.bankingcore.bankingledger.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * JpaConfig — activates Spring Data JPA features.
 *
 * @EnableJpaAuditing :
 *   Activates the AuditingEntityListener and wires AuditorAwareImpl.
 *   Without this, @CreatedDate / @CreatedBy annotations on BaseEntity do nothing.
 *   auditorAwareRef must match the bean name of AuditorAwareImpl.
 *
 * @EnableTransactionManagement :
 *   Enables @Transactional on service methods. Spring Boot auto-configures
 *   this, but declaring it explicitly documents the intent and allows
 *   fine-tuning (e.g. proxy mode) in the future.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
@EnableTransactionManagement
public class JpaConfig {
    // No bean definitions needed — annotations do all the work.
}