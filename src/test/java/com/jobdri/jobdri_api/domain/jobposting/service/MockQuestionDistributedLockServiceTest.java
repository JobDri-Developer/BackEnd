package com.jobdri.jobdri_api.domain.jobposting.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MockQuestionDistributedLockServiceTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final MockQuestionCacheProperties properties = MockQuestionCachePropertiesTestSupport.createProperties();
    private final MockQuestionDistributedLockService lockService = new MockQuestionDistributedLockService(redisTemplate, properties);

    @AfterEach
    void tearDown() {
        lockService.shutdown();
    }

    @Test
    @DisplayName("락을 획득하면 lease 만료 전에 자동 갱신을 시도한다")
    void tryAcquireAutoRenewsLeaseUntilClose() throws Exception {
        properties.setLockTtlMillis(3_000L);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("mock-question-lock:cache-key"), any(), eq(3_000L), eq(java.util.concurrent.TimeUnit.MILLISECONDS)))
                .thenReturn(true);
        when(redisTemplate.execute(any(), eq(List.of("mock-question-lock:cache-key")), any(), any()))
                .thenReturn(1L);
        when(redisTemplate.execute(any(), eq(List.of("mock-question-lock:cache-key")), any()))
                .thenReturn(1L);

        try (MockQuestionDistributedLockService.LockLease ignored = lockService.tryAcquire("cache-key")) {
            Thread.sleep(1_200L);
        }

        verify(redisTemplate, atLeast(1)).execute(any(), eq(List.of("mock-question-lock:cache-key")), any(), any());
        verify(redisTemplate, atLeast(1)).execute(any(), eq(List.of("mock-question-lock:cache-key")), any());
    }
}
