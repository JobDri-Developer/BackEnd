package com.jobdri.jobdri_api.domain.jobposting.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
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

    private final StringRedisTemplate redisTemplate;
    private final MockQuestionCacheProperties mockQuestionCacheProperties;

    public String tryAcquire(String cacheKey) {
        String lockToken = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                lockRedisKey(cacheKey),
                lockToken,
                mockQuestionCacheProperties.getLockTtlMillis(),
                TimeUnit.MILLISECONDS
        );
        return Boolean.TRUE.equals(acquired) ? lockToken : null;
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

    private String lockRedisKey(String cacheKey) {
        return LOCK_KEY_PREFIX + cacheKey;
    }
}
