package com.sentinel.ratelimiter.repository;

import com.sentinel.ratelimiter.entity.AbuseViolationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AbuseViolationRepository extends JpaRepository<AbuseViolationRecord, Long> {

    List<AbuseViolationRecord> findByClientIdOrderByCreatedAtDesc(String clientId);

    long countByClientIdAndCreatedAtAfter(String clientId, Instant since);

    void deleteByCreatedAtBefore(Instant cutoff);
}