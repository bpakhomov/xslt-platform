package com.example.xsltconverter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.params.SetParams;

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
public class ConversionResultCache {

    private static final Logger log = LoggerFactory.getLogger(ConversionResultCache.class);
    private static final String KEY_PREFIX = "xsltconverter:convert:";

    private final RedisClient redisClient;
    private final long ttlSeconds;
    private final int compressThresholdBytes;

    public ConversionResultCache(
            RedisClient redisClient,
            @Value("${cache.ttl-minutes:10}") long ttlMinutes,
            @Value("${cache.compress-threshold-bytes:4096}") int compressThresholdBytes
    ) {
        this.redisClient = redisClient;
        this.ttlSeconds = Duration.ofMinutes(ttlMinutes).toSeconds();
        this.compressThresholdBytes = compressThresholdBytes;
    }

    public Optional<String> get(String xmlContent, ConversionType type) {
        if (xmlContent == null || xmlContent.isBlank() || type == null || ttlSeconds <= 0) {
            return Optional.empty();
        }
        try {
            byte[] payload = redisClient.get(buildKey(xmlContent, type));
            if (payload == null) {
                return Optional.empty();
            }
            return Optional.of(new String(decompress(payload), StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Failed to read conversion cache from Redis", e);
            return Optional.empty();
        }
    }

    public void put(String xmlContent, ConversionType type, String result) {
        if (xmlContent == null || xmlContent.isBlank() || type == null || result == null || ttlSeconds <= 0) {
            return;
        }
        try {
            byte[] payload = compressIfNeeded(result.getBytes(StandardCharsets.UTF_8));
            redisClient.set(buildKey(xmlContent, type), payload, SetParams.setParams().ex(ttlSeconds));
        } catch (Exception e) {
            log.warn("Failed to write conversion cache to Redis", e);
        }
    }

    private byte[] buildKey(String xmlContent, ConversionType type) {
        String key = KEY_PREFIX + type.name() + ":" + sha256(xmlContent.trim());
        return key.getBytes(StandardCharsets.UTF_8);
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
