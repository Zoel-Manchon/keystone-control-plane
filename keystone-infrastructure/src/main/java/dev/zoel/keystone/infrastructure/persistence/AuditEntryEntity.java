package dev.zoel.keystone.infrastructure.persistence;

import dev.zoel.keystone.domain.audit.AuditAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "audit_log")
public class AuditEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sequence", nullable = false)
    private Long sequence;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "actor", nullable = false, length = 128)
    private String actor;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 64)
    private AuditAction action;

    @Column(name = "subject", nullable = false, length = 128)
    private String subject;

    @Column(name = "detail", nullable = false, length = 512)
    private String detail;

    @Column(name = "previous_hash", nullable = false, length = 64)
    private String previousHash;

    @Column(name = "entry_hash", nullable = false, length = 64)
    private String entryHash;

    protected AuditEntryEntity() {
        // required by JPA
    }

    public AuditEntryEntity(Instant occurredAt, String actor, AuditAction action, String subject,
                            String detail, String previousHash, String entryHash) {
        this.occurredAt = occurredAt;
        this.actor = actor;
        this.action = action;
        this.subject = subject;
        this.detail = detail;
        this.previousHash = previousHash;
        this.entryHash = entryHash;
    }

    public Long getSequence() { return sequence; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getActor() { return actor; }
    public AuditAction getAction() { return action; }
    public String getSubject() { return subject; }
    public String getDetail() { return detail; }
    public String getPreviousHash() { return previousHash; }
    public String getEntryHash() { return entryHash; }

    void applyHashes(String previousHash, String entryHash) {
        this.previousHash = previousHash;
        this.entryHash = entryHash;
    }
}
