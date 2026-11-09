package com.cgp.refunds.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "idempotency_records",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_idempotency_key",
            columnNames = "idempotency_key"
        )
    }
)
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(
        name = "idempotency_key",
        nullable = false,
        length = 128
    )
    private String key;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column
    private UUID refundId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyRecord() {
    }

    public IdempotencyRecord(String key, String fingerprint) {
        this.key = key;
        this.fingerprint = fingerprint;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public UUID getRefundId() {
        return refundId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void complete(UUID refundId) {
        this.refundId = refundId;
    }
}
