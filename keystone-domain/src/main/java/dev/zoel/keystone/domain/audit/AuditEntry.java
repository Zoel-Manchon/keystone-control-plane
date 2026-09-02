package dev.zoel.keystone.domain.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

/**
 * One link in a hash-chained audit trail.
 *
 * Each entry hashes its own content together with the hash of the entry before it.
 * Deleting or editing a row therefore breaks every hash from that point on, which
 * turns "someone tampered with the log" from an act of faith into a computation.
 *
 * This is tamper-EVIDENT, not tamper-PROOF: an attacker with write access to the
 * whole table could recompute the entire chain. Making that impossible needs an
 * external anchor (periodically publishing the head hash somewhere append-only).
 * Worth knowing the limit of what this buys you.
 */
public final class AuditEntry {

    /** The chain has to start somewhere. This is the hash the first entry links to. */
    public static final String GENESIS_HASH = "0".repeat(64);

    private final long sequence;
    private final Instant occurredAt;
    private final String actor;
    private final AuditAction action;
    private final String subject;
    private final String detail;
    private final String previousHash;
    private final String entryHash;

    private AuditEntry(long sequence, Instant occurredAt, String actor, AuditAction action,
                       String subject, String detail, String previousHash, String entryHash) {
        this.sequence = sequence;
        this.occurredAt = Objects.requireNonNull(occurredAt);
        this.actor = Objects.requireNonNull(actor);
        this.action = Objects.requireNonNull(action);
        this.subject = Objects.requireNonNull(subject);
        this.detail = detail == null ? "" : detail;
        this.previousHash = Objects.requireNonNull(previousHash);
        this.entryHash = Objects.requireNonNull(entryHash);
    }

    /** Links a new entry onto the current head of the chain. */
    public static AuditEntry link(long sequence, Instant occurredAt, String actor,
                                  AuditAction action, String subject, String detail,
                                  String previousHash) {
        String safeDetail = detail == null ? "" : detail;
        String hash = computeHash(sequence, occurredAt, actor, action, subject, safeDetail, previousHash);
        return new AuditEntry(sequence, occurredAt, actor, action, subject, safeDetail, previousHash, hash);
    }

    /** Rehydration from persistence: the stored hash is kept as-is so it can be verified. */
    public static AuditEntry rehydrate(long sequence, Instant occurredAt, String actor,
                                       AuditAction action, String subject, String detail,
                                       String previousHash, String entryHash) {
        return new AuditEntry(sequence, occurredAt, actor, action, subject, detail, previousHash, entryHash);
    }

    /** Recomputes the hash and compares. False means the row was altered after it was written. */
    public boolean hasIntactHash() {
        return entryHash.equals(
            computeHash(sequence, occurredAt, actor, action, subject, detail, previousHash));
    }

    /** True when this entry correctly follows the one handed in. */
    public boolean follows(AuditEntry previous) {
        return previous == null
            ? GENESIS_HASH.equals(previousHash)
            : previous.entryHash().equals(previousHash) && previous.sequence() < sequence;
    }

    private static String computeHash(long sequence, Instant occurredAt, String actor,
                                      AuditAction action, String subject, String detail,
                                      String previousHash) {
        // The separator matters: without it, ("ab","c") and ("a","bc") would hash the
        // same and an attacker could shuffle field boundaries without breaking the chain.
        String payload = String.join("\u001f",
            Long.toString(sequence),
            Long.toString(occurredAt.toEpochMilli()),
            actor, action.name(), subject, detail, previousHash);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JVM", e);
        }
    }

    public long sequence() { return sequence; }
    public Instant occurredAt() { return occurredAt; }
    public String actor() { return actor; }
    public AuditAction action() { return action; }
    public String subject() { return subject; }
    public String detail() { return detail; }
    public String previousHash() { return previousHash; }
    public String entryHash() { return entryHash; }
}
