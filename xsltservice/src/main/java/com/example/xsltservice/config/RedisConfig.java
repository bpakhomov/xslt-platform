package com.example.xsltservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.RedisClient;

@Configuration
public class RedisConfig {

    @Bean(destroyMethod = "close")
    public RedisClient redisClient(
            @Value("${redis.host}") String host,
            @Value("${redis.port}") int port,
            @Value("${redis.timeout-ms:2000}") int timeoutMs
    ) {
        return RedisClient.builder()
                .hostAndPort(host, port)
                .clientConfig(DefaultJedisClientConfig.builder().timeoutMillis(timeoutMs).build())
                .build();
    }
}
