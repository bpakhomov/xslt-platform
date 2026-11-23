package com.example.xsltconverter.service;

import com.example.xsltconverter.entity.User;
import com.example.xsltconverter.entity.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.SetParams;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Optional;

@Component
public class UserCache {

    private static final Logger log = LoggerFactory.getLogger(UserCache.class);
    private static final String KEY_PREFIX = "xsltconverter:auth:user:";

    private final RedisClient redisClient;
    private final ObjectMapper objectMapper;
    private final long ttlSeconds;

    public UserCache(
            RedisClient redisClient,
            ObjectMapper objectMapper,
            @Value("${auth.cache.ttl-minutes:5}") long ttlMinutes
    ) {
        this.redisClient = redisClient;
        this.objectMapper = objectMapper;
        this.ttlSeconds = Duration.ofMinutes(ttlMinutes).toSeconds();
    }

    public Optional<User> get(String username) {
        if (!canUseKey(username)) {
            return Optional.empty();
        }
        try {
            String key = buildKey(username);
            String payload = redisClient.get(key);
            return deserialize(payload);
        } catch (Exception e) {
            log.warn("Failed to read user cache from Redis", e);
            return Optional.empty();
        }
    }

    public void put(User user) {
        if (!isCacheable(user)) {
            return;
        }
        try {
            String key = buildKey(user.getUsername());
            String payload = serialize(user);
            SetParams params = SetParams.setParams().ex(ttlSeconds);
            redisClient.set(key, payload, params);
        } catch (Exception e) {
            log.warn("Failed to write user cache to Redis", e);
        }
    }

    public void evict(String username) {
        if (!canUseKey(username)) {
            return;
        }
        try {
            String key = buildKey(username);
            redisClient.del(key);
        } catch (Exception e) {
            log.warn("Failed to evict user cache from Redis", e);
        }
    }

    private Optional<User> deserialize(String payload) {
        if (isBlank(payload)) {
            return Optional.empty();
        }
        CachedUser cached = objectMapper.readValue(payload, CachedUser.class);
        if (cached == null || isBlank(cached.username()) || isBlank(cached.password()) || cached.status() == null) {
            return Optional.empty();
        }
        User user = toUser(cached);
        return Optional.of(user);
    }

    private String serialize(User user) {
        CachedUser cached = new CachedUser(user.getId(), user.getUsername(), user.getPassword(), user.getStatus());
        return objectMapper.writeValueAsString(cached);
    }

    private static User toUser(CachedUser cached) {
        User user = new User(cached.username(), cached.password());
        user.setId(cached.id());
        user.setStatus(cached.status());
        return user;
    }

    private boolean isCacheable(User user) {
        return user != null && canUseKey(user.getUsername()) && !isBlank(user.getPassword()) && user.getStatus() != null;
    }

    private boolean canUseKey(String username) {
        return ttlSeconds > 0 && !isBlank(username);
    }

    private static String buildKey(String username) {
        return KEY_PREFIX + username;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CachedUser(long id, String username, String password, UserStatus status) {
    }
}
