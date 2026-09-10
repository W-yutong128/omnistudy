package com.omnistudy.service;

import com.omnistudy.exception.QuotaExceededException;
import com.omnistudy.model.entity.UsageRecord;
import com.omnistudy.model.entity.UserQuota;
import com.omnistudy.repository.UsageRecordRepository;
import com.omnistudy.repository.UserQuotaRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentUsageServiceTest {
    private final UserQuotaRepository quotas = mock(UserQuotaRepository.class);
    private final UsageRecordRepository usage = mock(UsageRecordRepository.class);
    private final AgentUsageService service = new AgentUsageService(quotas, usage);

    @Test
    void rejectsWhenDailyRequestLimitIsReached() {
        UUID userId = UUID.randomUUID();
        when(quotas.findByUserIdForUpdate(userId)).thenReturn(Optional.of(UserQuota.builder()
                .userId(userId).dailyRequestLimit(2).dailyTokenLimit(10_000L).build()));
        when(usage.sumRequestsSince(eq(userId), any())).thenReturn(2L);
        when(usage.sumTokensSince(eq(userId), any())).thenReturn(100L);

        assertThrows(QuotaExceededException.class, () -> service.begin(userId, 100));
        verify(usage, never()).save(any(UsageRecord.class));
    }

    @Test
    void existingOperationUsesTokensWithoutConsumingAnotherRequest() {
        UUID userId = UUID.randomUUID();
        when(quotas.findByUserIdForUpdate(userId)).thenReturn(Optional.of(UserQuota.builder()
                .userId(userId).dailyRequestLimit(1).dailyTokenLimit(10_000L).build()));
        when(usage.sumRequestsSince(eq(userId), any())).thenReturn(1L);
        when(usage.sumTokensSince(eq(userId), any())).thenReturn(100L);
        when(usage.existsByUserIdAndOperationKeyAndRequestCountGreaterThan(
                userId, "NOTE_SESSION:abc", 0)).thenReturn(true);
        when(usage.save(any(UsageRecord.class))).thenAnswer(invocation -> {
            UsageRecord record = invocation.getArgument(0);
            record.setId(UUID.randomUUID());
            return record;
        });

        service.beginOperation(userId, 500, "NOTE_FINALIZE", "NOTE_SESSION:abc");

        verify(usage).save(argThat(record -> {
            assertEquals(0, record.getRequestCount());
            assertEquals(500L, record.getTotalTokens());
            assertEquals("NOTE_SESSION:abc", record.getOperationKey());
            return true;
        }));
    }

    @Test
    void internalModelCallConsumesTokensWithoutConsumingRequest() {
        UUID userId = UUID.randomUUID();
        when(quotas.findByUserIdForUpdate(userId)).thenReturn(Optional.of(UserQuota.builder()
                .userId(userId).dailyRequestLimit(1).dailyTokenLimit(10_000L).build()));
        when(usage.sumRequestsSince(eq(userId), any())).thenReturn(1L);
        when(usage.sumTokensSince(eq(userId), any())).thenReturn(100L);
        when(usage.save(any(UsageRecord.class))).thenAnswer(invocation -> {
            UsageRecord record = invocation.getArgument(0);
            record.setId(UUID.randomUUID());
            return record;
        });

        service.beginInternal(userId, 750, "CONTEXT_COMPACTION");

        verify(usage).save(argThat(record -> {
            assertEquals(0, record.getRequestCount());
            assertEquals(750L, record.getTotalTokens());
            assertEquals("CONTEXT_COMPACTION", record.getFeature());
            return true;
        }));
    }
}
