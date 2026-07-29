package com.jobdri.jobdri_api.domain.jobposting.service;

import lombok.RequiredArgsConstructor;
import jakarta.annotation.PreDestroy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MockQuestionDistributedLockService {

    private static final String LOCK_KEY_PREFIX = "mock-question-lock:";
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('del', KEYS[1])
            end
            return 0
            """,
            Long.class
    );
    private static final DefaultRedisScript<Long> RENEW_LOCK_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('pexpire', KEYS[1], ARGV[2])
            end
            return 0
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final MockQuestionCacheProperties mockQuestionCacheProperties;
    private final ScheduledExecutorService leaseRenewExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("mock-question-lock-renew-1");
        thread.setDaemon(true);
        return thread;
    });

    public LockLease tryAcquire(String cacheKey) {
        String lockToken = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                lockRedisKey(cacheKey),
                lockToken,
                mockQuestionCacheProperties.getLockTtlMillis(),
                TimeUnit.MILLISECONDS
        );
        if (!Boolean.TRUE.equals(acquired)) {
            return null;
        }
        ScheduledFuture<?> renewFuture = leaseRenewExecutor.scheduleAtFixedRate(
                () -> renew(cacheKey, lockToken),
                renewIntervalMillis(),
                renewIntervalMillis(),
                TimeUnit.MILLISECONDS
        );
        return new LockLease(cacheKey, lockToken, renewFuture);
    }

    public void release(String cacheKey, String lockToken) {
        if (lockToken == null) {
            return;
        }
        redisTemplate.execute(
                RELEASE_LOCK_SCRIPT,
                List.of(lockRedisKey(cacheKey)),
                lockToken
        );
    }

    boolean renew(String cacheKey, String lockToken) {
        Long renewed = redisTemplate.execute(
                RENEW_LOCK_SCRIPT,
                List.of(lockRedisKey(cacheKey)),
                lockToken,
                String.valueOf(mockQuestionCacheProperties.getLockTtlMillis())
        );
        return renewed != null && renewed > 0;
    }

    private long renewIntervalMillis() {
        return Math.max(1_000L, mockQuestionCacheProperties.getLockTtlMillis() / 3L);
    }

    private String lockRedisKey(String cacheKey) {
        return LOCK_KEY_PREFIX + cacheKey;
    }

    @PreDestroy
    void shutdown() {
        leaseRenewExecutor.shutdownNow();
    }

    public final class LockLease implements AutoCloseable {

        private final String cacheKey;
        private final String lockToken;
        private final ScheduledFuture<?> renewFuture;

        private LockLease(String cacheKey, String lockToken, ScheduledFuture<?> renewFuture) {
            this.cacheKey = cacheKey;
            this.lockToken = lockToken;
            this.renewFuture = renewFuture;
        }

        public String lockToken() {
            return lockToken;
        }

        @Override
        public void close() {
            renewFuture.cancel(true);
            release(cacheKey, lockToken);
        }
    }
}
