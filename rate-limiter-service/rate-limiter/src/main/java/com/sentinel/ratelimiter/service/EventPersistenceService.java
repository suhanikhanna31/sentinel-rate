package com.sentinel.ratelimiter.service;

import com.sentinel.ratelimiter.entity.AbuseViolationRecord;
import com.sentinel.ratelimiter.entity.AbuseViolationRecord.BlockTier;
import com.sentinel.ratelimiter.entity.RateLimitEvent;
import com.sentinel.ratelimiter.repository.AbuseViolationRepository;
import com.sentinel.ratelimiter.repository.RateLimitEventRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class EventPersistenceService {

    private final RateLimitEventRepository eventRepo;
    private final AbuseViolationRepository violationRepo;

    public EventPersistenceService(RateLimitEventRepository eventRepo,
                                   AbuseViolationRepository violationRepo) {
        this.eventRepo     = eventRepo;
        this.violationRepo = violationRepo;
    }

    @Async
    @Transactional
    public void saveRateLimitEvent(String clientId, boolean allowed, int remaining) {
        eventRepo.save(new RateLimitEvent(clientId, allowed, remaining));
    }

    @Async
    @Transactional
    public void saveViolation(String clientId, long violationCount) {
        violationRepo.save(new AbuseViolationRecord(clientId, violationCount, resolveBlockTier(violationCount)));
    }

    @Transactional(readOnly = true)
    public List<RateLimitEvent> getEventsForClient(String clientId) {
        return eventRepo.findByClientIdOrderByCreatedAtDesc(clientId);
    }

    @Transactional(readOnly = true)
    public List<AbuseViolationRecord> getViolationsForClient(String clientId) {
        return violationRepo.findByClientIdOrderByCreatedAtDesc(clientId);
    }

    @Transactional(readOnly = true)
    public long countRejectedLast60s(String clientId) {
        return eventRepo.countRejectedSince(clientId, Instant.now().minusSeconds(60));
    }

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void pruneOldRecords() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.DAYS);
        eventRepo.deleteByCreatedAtBefore(cutoff);
        violationRepo.deleteByCreatedAtBefore(cutoff);
    }

    private static BlockTier resolveBlockTier(long violations) {
        if (violations >= 10) return BlockTier.TIER_3;
        if (violations >= 6)  return BlockTier.TIER_2;
        if (violations >= 3)  return BlockTier.TIER_1;
        return BlockTier.NONE;
    }
}