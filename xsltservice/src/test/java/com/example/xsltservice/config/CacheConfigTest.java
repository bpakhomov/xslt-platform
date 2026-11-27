package com.example.xsltservice.config;

import com.example.xsltservice.entity.TariffPlan;
import com.example.xsltservice.entity.User;
import com.example.xsltservice.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class CacheConfigTest {

    private RedisCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        CacheConfig config = new CacheConfig();
        cacheManager = config.cacheManager(mock(RedisConnectionFactory.class), 10, 5);
        cacheManager.initializeCaches();
    }

    @Nested
    class UsersCache {

        @Test
        void configuresUsersCacheTtlAndRoundTripsUserWithoutActiveField() {
            RedisCacheConfiguration usersConfig = cacheManager.getCacheConfigurations().get("users");
            assertNotNull(usersConfig);
            assertEquals(Duration.ofMinutes(5), usersConfig.getTtlFunction().getTimeToLive("alice", null));

            User user = new User("alice", "$2a$10$hash");
            user.setId(42L);
            user.setStatus(UserStatus.DISABLED);
            user.setTariffPlan(TariffPlan.PROFESSIONAL);

            var pair = usersConfig.getValueSerializationPair();
            ByteBuffer serialized = pair.write(user);
            String json = StandardCharsets.UTF_8.decode(serialized.asReadOnlyBuffer()).toString();
            User restored = (User) pair.read(serialized);

            assertFalse(json.contains("\"active\""));
            assertEquals(42L, restored.getId());
            assertEquals("alice", restored.getUsername());
            assertEquals("$2a$10$hash", restored.getPassword());
            assertEquals(UserStatus.DISABLED, restored.getStatus());
            assertEquals(TariffPlan.PROFESSIONAL, restored.getTariffPlan());
        }
    }
}
