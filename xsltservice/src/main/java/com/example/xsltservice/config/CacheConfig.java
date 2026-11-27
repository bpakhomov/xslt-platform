package com.example.xsltservice.config;

import com.example.xsltservice.dto.response.ConversionResponseDto;
import com.example.xsltservice.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final String USERS_CACHE = "users";
    private static final String CONVERSIONS_CACHE = "conversions";

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory,
            @Value("${cache.ttl-minutes:10}") long cacheTtlMinutes,
            @Value("${auth.cache.ttl-minutes:5}") long authCacheTtlMinutes
    ) {
        RedisCacheConfiguration defaultConfig = buildDefaultConfiguration(cacheTtlMinutes);

        Map<String, RedisCacheConfiguration> perCache = Map.of(
                USERS_CACHE, defaultConfig
                        .entryTtl(Duration.ofMinutes(authCacheTtlMinutes))
                        .serializeValuesWith(
                                SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(User.class))
                        ),
                CONVERSIONS_CACHE, defaultConfig.serializeValuesWith(
                        SerializationPair.fromSerializer(new JacksonJsonRedisSerializer<>(ConversionResponseDto.class))
                )
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
