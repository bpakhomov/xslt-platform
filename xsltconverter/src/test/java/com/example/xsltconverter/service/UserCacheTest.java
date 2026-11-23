package com.example.xsltconverter.service;

import com.example.xsltconverter.entity.User;
import com.example.xsltconverter.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.SetParams;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserCacheTest {

    private RedisClient redisClient;
    private ObjectMapper objectMapper;
    private UserCache userCache;

    @BeforeEach
    void setUp() {
        redisClient = mock(RedisClient.class);
        objectMapper = new ObjectMapper();
        userCache = new UserCache(redisClient, objectMapper, 5);
    }

    @Nested
    class Get {

        @Test
        void returnsUserWhenCached() {
            when(redisClient.get("xsltconverter:auth:user:alice"))
                    .thenReturn("{\"id\":7,\"username\":\"alice\",\"password\":\"hashed\",\"status\":\"ACTIVE\"}");

            Optional<User> result = userCache.get("alice");

            assertTrue(result.isPresent());
            assertEquals(7L, result.get().getId());
            assertEquals("alice", result.get().getUsername());
            assertEquals("hashed", result.get().getPassword());
            assertEquals(UserStatus.ACTIVE, result.get().getStatus());
        }

        @Test
        void returnsDisabledUserWhenCachedAsDisabled() {
            when(redisClient.get("xsltconverter:auth:user:alice"))
                    .thenReturn("{\"id\":7,\"username\":\"alice\",\"password\":\"hashed\",\"status\":\"DISABLED\"}");

            Optional<User> result = userCache.get("alice");

            assertTrue(result.isPresent());
            assertEquals(UserStatus.DISABLED, result.get().getStatus());
            assertFalse(result.get().isActive());
        }

        @Test
        void returnsEmptyWhenPayloadMissingStatus() {
            when(redisClient.get("xsltconverter:auth:user:alice"))
                    .thenReturn("{\"id\":7,\"username\":\"alice\",\"password\":\"hashed\"}");

            assertTrue(userCache.get("alice").isEmpty());
        }

        @Test
        void returnsEmptyWhenCacheMiss() {
            when(redisClient.get("xsltconverter:auth:user:alice")).thenReturn(null);

            assertTrue(userCache.get("alice").isEmpty());
        }

        @Test
        void returnsEmptyWhenJsonIsMalformed() {
            when(redisClient.get("xsltconverter:auth:user:alice")).thenReturn("{broken-json");

            assertTrue(userCache.get("alice").isEmpty());
        }

        @Test
        void returnsEmptyWhenPayloadMissingPassword() {
            when(redisClient.get("xsltconverter:auth:user:alice"))
                    .thenReturn("{\"id\":7,\"username\":\"alice\",\"password\":\"\"}");

            assertTrue(userCache.get("alice").isEmpty());
        }

        @Test
        void returnsEmptyWhenRedisThrows() {
            when(redisClient.get("xsltconverter:auth:user:alice"))
                    .thenThrow(new RuntimeException("Redis unavailable"));

            assertTrue(userCache.get("alice").isEmpty());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void returnsEmptyWhenUsernameIsBlank(String username) {
            assertTrue(userCache.get(username).isEmpty());
            verifyNoInteractions(redisClient);
        }

        @ParameterizedTest
        @ValueSource(longs = {0, -1})
        void skipsRedisWhenTtlIsNotPositive(long ttlMinutes) {
            UserCache disabledCache = new UserCache(redisClient, objectMapper, ttlMinutes);

            assertTrue(disabledCache.get("alice").isEmpty());
            verifyNoInteractions(redisClient);
        }
    }

    @Nested
    class Put {

        @Test
        void writesSerializedUserWithTtl() {
            User user = new User("alice", "hashed");
            user.setId(7L);
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

            userCache.put(user);

            verify(redisClient).set(
                    eq("xsltconverter:auth:user:alice"),
                    payloadCaptor.capture(),
                    eq(SetParams.setParams().ex(300))
            );
            JsonNode json = objectMapper.readTree(payloadCaptor.getValue());
            assertEquals(7L, json.path("id").asLong());
            assertEquals("alice", json.path("username").asString());
            assertEquals("hashed", json.path("password").asString());
            assertEquals("ACTIVE", json.path("status").asString());
        }

        @Test
        void doesNothingWhenStatusIsNull() {
            User user = new User("alice", "hashed");
            user.setStatus(null);

            userCache.put(user);

            verifyNoInteractions(redisClient);
        }

        @Test
        void doesNothingWhenUserIsNull() {
            userCache.put(null);

            verifyNoInteractions(redisClient);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void doesNothingWhenUsernameIsBlank(String username) {
            User user = new User(username, "hashed");

            userCache.put(user);

            verifyNoInteractions(redisClient);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void doesNothingWhenPasswordIsBlank(String password) {
            User user = new User("alice", password);

            userCache.put(user);

            verifyNoInteractions(redisClient);
        }

        @ParameterizedTest
        @ValueSource(longs = {0, -2})
        void skipsRedisWhenTtlIsNotPositive(long ttlMinutes) {
            UserCache disabledCache = new UserCache(redisClient, objectMapper, ttlMinutes);
            User user = new User("alice", "hashed");

            disabledCache.put(user);

            verifyNoInteractions(redisClient);
        }

        @Test
        void swallowsRedisExceptionOnWrite() {
            User user = new User("alice", "hashed");
            when(redisClient.set(anyString(), anyString(), any(SetParams.class)))
                    .thenThrow(new RuntimeException("Redis unavailable"));

            assertDoesNotThrow(() -> userCache.put(user));
        }
    }

    @Nested
    class Evict {

        @Test
        void deletesKeyFromRedis() {
            userCache.evict("alice");

            verify(redisClient).del("xsltconverter:auth:user:alice");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void doesNothingWhenUsernameIsBlank(String username) {
            userCache.evict(username);

            verifyNoInteractions(redisClient);
        }

        @ParameterizedTest
        @ValueSource(longs = {0, -1})
        void skipsRedisWhenTtlIsNotPositive(long ttlMinutes) {
            UserCache disabledCache = new UserCache(redisClient, objectMapper, ttlMinutes);

            disabledCache.evict("alice");

            verifyNoInteractions(redisClient);
        }

        @Test
        void swallowsRedisExceptionOnEvict() {
            when(redisClient.del(anyString())).thenThrow(new RuntimeException("Redis unavailable"));

            assertDoesNotThrow(() -> userCache.evict("alice"));
        }
    }
}
