package com.example.xsltconverter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final String TOKENS_CACHE = "tokens";

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Value("${cache.ttl-minutes:10}") long cacheTtlMinutes,
            @Value("${auth.cache.ttl-minutes:5}") long authCacheTtlMinutes
    ) {
        RedisCacheConfiguration defaultConfig = buildDefaultConfiguration(cacheTtlMinutes);
        Map<String, RedisCacheConfiguration> perCache = Map.of(
                TOKENS_CACHE, defaultConfig.entryTtl(Duration.ofMinutes(authCacheTtlMinutes))
        );
        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    private RedisCacheConfiguration buildDefaultConfiguration(long ttlMinutes) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(ttlMinutes))
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(RedisSerializer.string()))
                .serializeValuesWith(SerializationPair.fromSerializer(RedisSerializer.json()));
    }
}
