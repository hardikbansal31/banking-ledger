package com.bankingcore.bankingledger.domain.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * BaseEntity — Abstract superclass for all JPA-managed domain entities.
 *
 * <p>Provides:
 * <ul>
 *   <li><b>UUID primary key</b> — generated before persist; avoids Hibernate
 *       sequence-table round-trips and plays well with distributed systems.</li>
 *   <li><b>Optimistic locking</b> via {@code @Version} — prevents lost-update
 *       anomalies in concurrent ledger operations without full table locks.</li>
 *   <li><b>JPA Auditing</b> — {@code createdAt}, {@code updatedAt},
 *       {@code createdBy}, {@code updatedBy} auto-populated by Spring Data.</li>
 *   <li><b>Soft-delete support</b> — {@code deleted} flag + {@code deletedAt}
 *       timestamp; actual DB row is never removed (required for audit trails
 *       in regulated banking environments).</li>
 * </ul>
 *
 * <p><b>Important:</b> All subclasses must declare their own
 * {@code @Table(name = "...")} annotation. This class is mapped
 * {@code SINGLE_TABLE} inheritance by default; override with
 * {@code @Inheritance} on the subclass if a different strategy is needed.
 *
 * <p>Usage:
 * <pre>{@code
 * @Entity
 * @Table(name = "accounts")
 * public class Account extends BaseEntity { ... }
 * }</pre>
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity implements Serializable {

    // ─────────────────────────────────────────────────────────────────────────
    // Primary Key
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * UUID primary key assigned in Java before the INSERT statement fires.
     * Using {@link GenerationType#UUID} (Hibernate 6+) delegates to
     * {@code org.hibernate.id.uuid.UuidGenerator} which uses random UUIDs.
     * Stored as BINARY(16) on MySQL for compact indexing.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    // ─────────────────────────────────────────────────────────────────────────
    // Optimistic Locking
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Optimistic lock counter. Hibernate increments this on every UPDATE.
     * A stale-object write will throw {@link jakarta.persistence.OptimisticLockException},
     * which the service layer converts to a 409 Conflict response.
     */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // ─────────────────────────────────────────────────────────────────────────
    // Audit Timestamps  (populated by Spring Data AuditingEntityListener)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * UTC instant at which this record was first persisted.
     * {@code updatable = false} ensures Hibernate never issues an UPDATE for this column.
     */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * UTC instant of the most recent UPDATE. Auto-maintained by Spring Data auditing.
     */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ─────────────────────────────────────────────────────────────────────────
    // Audit Principal  (populated by AuditorAware bean — wired in Phase 2)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Username / principal ID of the actor who created this record.
     * Resolved from the Spring Security context via {@code AuditorAware<String>}.
     */
    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    /**
     * Username / principal ID of the last actor to modify this record.
     */
    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    // ─────────────────────────────────────────────────────────────────────────
    // Soft Delete
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Soft-delete flag. Set to {@code true} instead of issuing a DELETE statement.
     * All repository queries must filter on {@code deleted = false}; apply a
     * {@code @Where(clause = "deleted = false")} on concrete entity classes
     * or use {@code @SQLRestriction} (Hibernate 6.3+).
     */
    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    /**
     * UTC timestamp of when this record was logically deleted.
     * {@code null} when the record is active.
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle Hooks
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Initialise non-nullable fields that Spring Data auditing doesn't cover
     * (e.g. {@code deleted}) before the first INSERT.
     */
    @PrePersist
    protected void onPrePersist() {
        if (this.deleted) {
            throw new IllegalStateException(
                    "Cannot persist an entity that is already marked as deleted.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Equality — based on id only (never on mutable state)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Two entities are equal iff they share the same non-null {@link #id}.
     * This is intentionally <em>not</em> Lombok-generated to avoid pitfalls
     * with Hibernate proxies and transient (id == null) entities.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof BaseEntity other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        // Fixed hash for transient instances; stable once id is assigned.
        return id == null ? System.identityHashCode(this) : id.hashCode();
    }

    @Override
    public String toString() {
        return "%s{id=%s, version=%d, deleted=%b}"
                .formatted(getClass().getSimpleName(), id, version, deleted);
    }
}