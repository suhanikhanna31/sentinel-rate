package com.sentinel.ratelimiter.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "rate_limit_events")
public class RateLimitEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 128)
    private String clientId;

    @Column(nullable = false)
    private boolean allowed;

    @Column(name = "remaining_tokens", nullable = false)
    private int remainingTokens;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected RateLimitEvent() {}

    public RateLimitEvent(String clientId, boolean allowed, int remainingTokens) {
        this.clientId        = clientId;
        this.allowed         = allowed;
        this.remainingTokens = remainingTokens;
        this.createdAt       = Instant.now();
    }

    public Long    getId()              { return id; }
    public String  getClientId()        { return clientId; }
    public boolean isAllowed()          { return allowed; }
    public int     getRemainingTokens() { return remainingTokens; }
    public Instant getCreatedAt()       { return createdAt; }
}