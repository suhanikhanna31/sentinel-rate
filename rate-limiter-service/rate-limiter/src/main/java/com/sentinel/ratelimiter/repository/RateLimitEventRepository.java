package com.sentinel.ratelimiter.repository;

import com.sentinel.ratelimiter.entity.RateLimitEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface RateLimitEventRepository extends JpaRepository<RateLimitEvent, Long> {

    List<RateLimitEvent> findByClientIdOrderByCreatedAtDesc(String clientId);

    @Query("""
        SELECT COUNT(e) FROM RateLimitEvent e
        WHERE e.clientId = :clientId
          AND e.allowed  = false
          AND e.createdAt >= :since
        """)
    long countRejectedSince(@Param("clientId") String clientId, @Param("since") Instant since);

    void deleteByCreatedAtBefore(Instant cutoff);
}