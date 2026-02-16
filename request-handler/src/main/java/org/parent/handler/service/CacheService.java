package org.parent.handler.service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CacheService {

    @Autowired
    private RedisTemplate<String, byte[]> byteRedisTemplate;

    private static final String CACHE_PREFIX = "file:";

    public byte[] get(String key) {
        return byteRedisTemplate.opsForValue().get(CACHE_PREFIX + key);
    }

    public void set(String key, byte[] content) {
        byteRedisTemplate.opsForValue().set(
                CACHE_PREFIX + key,
                content,
                1,
                TimeUnit.HOURS
        );
    }
}