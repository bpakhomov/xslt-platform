package com.example.xsltservice.service;

import com.example.xsltservice.dto.response.ConversionResponseDto;
import com.example.xsltservice.entity.ConversionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.SetParams;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Component
public class ConversionCache {

    private static final Logger log = LoggerFactory.getLogger(ConversionCache.class);
    private static final String REQUEST_KEY_PREFIX = "xsltservice:request:";
    private static final String RESULT_KEY_PREFIX = "xsltservice:result:";

    private final RedisClient redisClient;
    private final ObjectMapper objectMapper;
    private final long ttlSeconds;
    private final int compressThresholdBytes;

    public ConversionCache(
            RedisClient redisClient,
            ObjectMapper objectMapper,
            @Value("${cache.ttl-minutes:10}") long ttlMinutes,
            @Value("${cache.compress-threshold-bytes:4096}") int compressThresholdBytes
    ) {
        this.redisClient = redisClient;
        this.objectMapper = objectMapper;
        this.ttlSeconds = Duration.ofMinutes(ttlMinutes).toSeconds();
        this.compressThresholdBytes = compressThresholdBytes;
    }

    public Optional<ConversionResponseDto> get(String xmlContent, ConversionType type) {
        if (xmlContent == null || xmlContent.isBlank() || type == null || ttlSeconds <= 0) {
            return Optional.empty();
        }
        try {
            byte[] payload = readBytes(buildRequestKey(xmlContent, type));
            return payload != null
                    ? Optional.of(objectMapper.readValue(payload, ConversionResponseDto.class))
                    : Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to read conversion cache from Redis", e);
            return Optional.empty();
        }
    }

    public void put(String xmlContent, ConversionType type, ConversionResponseDto response) {
        if (xmlContent == null || xmlContent.isBlank() || type == null || response == null || ttlSeconds <= 0) {
            return;
        }
        try {
            writeBytes(buildRequestKey(xmlContent, type), objectMapper.writeValueAsBytes(response));
        } catch (Exception e) {
            log.warn("Failed to write conversion cache to Redis", e);
        }
    }

    public Optional<String> getResult(Long id) {
        if (id == null || ttlSeconds <= 0) {
            return Optional.empty();
        }
        try {
            byte[] payload = readBytes(buildResultKey(id));
            return payload != null ? Optional.of(new String(payload, StandardCharsets.UTF_8)) : Optional.empty();
        } catch (Exception e) {
            log.warn("Failed to read result cache from Redis for id={}", id, e);
            return Optional.empty();
        }
    }

    public void putResult(Long id, String result) {
        if (id == null || result == null || ttlSeconds <= 0) {
            return;
        }
        try {
            writeBytes(buildResultKey(id), result.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Failed to write result cache to Redis for id={}", id, e);
        }
    }

    private byte[] buildRequestKey(String xmlContent, ConversionType type) {
        String key = REQUEST_KEY_PREFIX + type.name() + ":" + sha256(xmlContent.trim());
        return key.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] buildResultKey(Long id) {
        return (RESULT_KEY_PREFIX + id).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] readBytes(byte[] key) throws IOException {
        byte[] payload = redisClient.get(key);
        return payload != null ? decompress(payload) : null;
    }

    private void writeBytes(byte[] key, byte[] raw) throws IOException {
        redisClient.set(key, compressIfNeeded(raw), SetParams.setParams().ex(ttlSeconds));
    }

    private byte[] compressIfNeeded(byte[] raw) throws IOException {
        if (compressThresholdBytes < 0 || raw.length < compressThresholdBytes) {
            return raw;
        }
        return gzip(raw);
    }

    private byte[] decompress(byte[] data) throws IOException {
        if (!isGzipped(data)) {
            return data;
        }
        return gunzip(data);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
        }
        return out.toByteArray();
    }

    private byte[] gunzip(byte[] data) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return gzip.readAllBytes();
        }
    }

    private boolean isGzipped(byte[] data) {
        return data.length >= 2 && data[0] == (byte) 0x1f && data[1] == (byte) 0x8b;
    }
}
