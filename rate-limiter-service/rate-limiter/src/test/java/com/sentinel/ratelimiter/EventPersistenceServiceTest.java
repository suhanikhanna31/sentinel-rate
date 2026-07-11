package com.sentinel.ratelimiter;

import com.sentinel.ratelimiter.entity.AbuseViolationRecord;
import com.sentinel.ratelimiter.entity.AbuseViolationRecord.BlockTier;
import com.sentinel.ratelimiter.entity.RateLimitEvent;
import com.sentinel.ratelimiter.repository.AbuseViolationRepository;
import com.sentinel.ratelimiter.repository.RateLimitEventRepository;
import com.sentinel.ratelimiter.service.EventPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EventPersistenceService} with both JPA repositories mocked out,
 * so they run without a database and pin down the tier-mapping and cutoff-date logic
 * directly (this class previously had no dedicated tests).
 */
@ExtendWith(MockitoExtension.class)
class EventPersistenceServiceTest {

    @Mock private RateLimitEventRepository eventRepo;
    @Mock private AbuseViolationRepository violationRepo;

    private EventPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        persistenceService = new EventPersistenceService(eventRepo, violationRepo);
    }

    @Test
    @DisplayName("saveRateLimitEvent persists a RateLimitEvent with the given fields")
    void saveRateLimitEventPersistsCorrectFields() {
        persistenceService.saveRateLimitEvent("client-a", true, 42);

        ArgumentCaptor<RateLimitEvent> captor = ArgumentCaptor.forClass(RateLimitEvent.class);
        verify(eventRepo).save(captor.capture());

        RateLimitEvent saved = captor.getValue();
        assertThat(saved.getClientId()).isEqualTo("client-a");
        assertThat(saved.isAllowed()).isTrue();
        assertThat(saved.getRemainingTokens()).isEqualTo(42);
    }

    @Test
    @DisplayName("saveViolation maps violation count 0-2 to BlockTier.NONE")
    void saveViolationMapsBelowThresholdToNone() {
        persistenceService.saveViolation("client-b", 2);

        ArgumentCaptor<AbuseViolationRecord> captor = ArgumentCaptor.forClass(AbuseViolationRecord.class);
        verify(violationRepo).save(captor.capture());
        assertThat(captor.getValue().getBlockTier()).isEqualTo(BlockTier.NONE);
    }

    @Test
    @DisplayName("saveViolation maps violation count 3-5 to BlockTier.TIER_1")
    void saveViolationMapsTierOne() {
        persistenceService.saveViolation("client-c", 3);

        ArgumentCaptor<AbuseViolationRecord> captor = ArgumentCaptor.forClass(AbuseViolationRecord.class);
        verify(violationRepo).save(captor.capture());
        assertThat(captor.getValue().getBlockTier()).isEqualTo(BlockTier.TIER_1);
    }

    @Test
    @DisplayName("saveViolation maps violation count 6-9 to BlockTier.TIER_2")
    void saveViolationMapsTierTwo() {
        persistenceService.saveViolation("client-d", 6);

        ArgumentCaptor<AbuseViolationRecord> captor = ArgumentCaptor.forClass(AbuseViolationRecord.class);
        verify(violationRepo).save(captor.capture());
        assertThat(captor.getValue().getBlockTier()).isEqualTo(BlockTier.TIER_2);
    }

    @Test
    @DisplayName("saveViolation maps violation count 10+ to BlockTier.TIER_3")
    void saveViolationMapsTierThree() {
        persistenceService.saveViolation("client-e", 10);

        ArgumentCaptor<AbuseViolationRecord> captor = ArgumentCaptor.forClass(AbuseViolationRecord.class);
        verify(violationRepo).save(captor.capture());
        assertThat(captor.getValue().getBlockTier()).isEqualTo(BlockTier.TIER_3);
    }

    @Test
    @DisplayName("getEventsForClient delegates to the repository")
    void getEventsForClientDelegates() {
        List<RateLimitEvent> events = List.of(new RateLimitEvent("client-f", true, 10));
        when(eventRepo.findByClientIdOrderByCreatedAtDesc("client-f")).thenReturn(events);

        assertThat(persistenceService.getEventsForClient("client-f")).isEqualTo(events);
    }

    @Test
    @DisplayName("getViolationsForClient delegates to the repository")
    void getViolationsForClientDelegates() {
        List<AbuseViolationRecord> violations =
                List.of(new AbuseViolationRecord("client-g", 3, BlockTier.TIER_1));
        when(violationRepo.findByClientIdOrderByCreatedAtDesc("client-g")).thenReturn(violations);

        assertThat(persistenceService.getViolationsForClient("client-g")).isEqualTo(violations);
    }

    @Test
    @DisplayName("countRejectedLast60s delegates with a since-timestamp roughly 60s in the past")
    void countRejectedLast60sDelegatesWithRecentCutoff() {
        when(eventRepo.countRejectedSince(eq("client-h"), any(Instant.class))).thenReturn(5L);

        long count = persistenceService.countRejectedLast60s("client-h");

        assertThat(count).isEqualTo(5L);

        ArgumentCaptor<Instant> sinceCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(eventRepo).countRejectedSince(eq("client-h"), sinceCaptor.capture());
        assertThat(sinceCaptor.getValue()).isCloseTo(Instant.now().minusSeconds(60), within(2000));
    }

    @Test
    @DisplayName("pruneOldRecords deletes events and violations older than 30 days from both repositories")
    void pruneOldRecordsDeletesFromBothRepositories() {
        persistenceService.pruneOldRecords();

        ArgumentCaptor<Instant> eventCutoff = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> violationCutoff = ArgumentCaptor.forClass(Instant.class);

        verify(eventRepo).deleteByCreatedAtBefore(eventCutoff.capture());
        verify(violationRepo).deleteByCreatedAtBefore(violationCutoff.capture());

        Instant expectedCutoff = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
        assertThat(eventCutoff.getValue()).isCloseTo(expectedCutoff, within(5000));
        assertThat(violationCutoff.getValue()).isCloseTo(expectedCutoff, within(5000));
    }

    private static org.assertj.core.data.TemporalUnitWithinOffset within(long millis) {
        return new org.assertj.core.data.TemporalUnitWithinOffset(millis, java.time.temporal.ChronoUnit.MILLIS);
    }
}
