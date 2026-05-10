package com.jobdri.jobdri_api.global.redis.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisExampleService {

    private final StringRedisTemplate redisTemplate;

    public void saveData(String key, String value) {
        redisTemplate.opsForValue().set(key, value);
        log.info("{} 에 {} 값을 저장했습니다.", key, value);
    }

    public String getData(String key) {
        String value = redisTemplate.opsForValue().get(key);
        log.info("{} 에서 {} 값을 조회했습니다.", key, value);
        return value;
    }

    public void deleteData(String key) {
        redisTemplate.delete(key);
        log.info("{} 키를 삭제했습니다.", key);
    }
}
