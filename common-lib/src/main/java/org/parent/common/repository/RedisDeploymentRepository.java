package org.parent.common.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.parent.common.entity.Deployment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class RedisDeploymentRepository implements DeploymentRepository {

    private static final String KEY_PREFIX = "deployment:";

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void save(Deployment deployment) {
        try {
            String json = objectMapper.writeValueAsString(deployment);
            redisTemplate.opsForValue().set(KEY_PREFIX + deployment.getId(), json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save deployment", e);
        }
    }

    @Override
    public Optional<Deployment> findById(String id) {
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + id);
            if (json == null) return Optional.empty();
            Deployment d = objectMapper.readValue(json, Deployment.class);
            return Optional.of(d);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read deployment", e);
        }
    }
}
