package org.parent.common.service;


import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class QueueService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String BUILD_QUEUE = "build_queue";

    /**
     * Add deployment to build queue
     */
    public void addToBuildQueue(String deploymentId) {
        redisTemplate.opsForList().rightPush(BUILD_QUEUE, deploymentId);
        log.info("Added to build queue: {}", deploymentId);
    }

    /**
     * Get next deployment from build queue (blocking)
     */
    public String getNextBuild() {
        return redisTemplate.opsForList().leftPop(BUILD_QUEUE, 5, TimeUnit.SECONDS);
    }

    /**
     * Get queue size
     */
    public long getQueueSize() {
        Long size = redisTemplate.opsForList().size(BUILD_QUEUE);
        return size != null ? size : 0;
    }
}