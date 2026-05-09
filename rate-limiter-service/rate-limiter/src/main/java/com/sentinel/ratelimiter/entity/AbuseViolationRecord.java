package com.sentinel.ratelimiter.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "abuse_violations")
public class AbuseViolationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 128)
    private String clientId;

    @Column(name = "violation_count", nullable = false)
    private long violationCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "block_tier", nullable = false, length = 16)
    private BlockTier blockTier;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum BlockTier { NONE, TIER_1, TIER_2, TIER_3 }

    protected AbuseViolationRecord() {}

    public AbuseViolationRecord(String clientId, long violationCount, BlockTier blockTier) {
        this.clientId       = clientId;
        this.violationCount = violationCount;
        this.blockTier      = blockTier;
        this.createdAt      = Instant.now();
    }

    public Long      getId()             { return id; }
    public String    getClientId()       { return clientId; }
    public long      getViolationCount() { return violationCount; }
    public BlockTier getBlockTier()      { return blockTier; }
    public Instant   getCreatedAt()      { return createdAt; }
}