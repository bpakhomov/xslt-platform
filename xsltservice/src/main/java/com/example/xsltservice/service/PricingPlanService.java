package com.example.xsltservice.service;

import com.example.xsltservice.entity.TariffPlan;
import com.example.xsltservice.entity.User;
import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PricingPlanService {

    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    private final Map<String, BucketEntry> buckets = new ConcurrentHashMap<>();

    public Bucket resolveBucket(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }
        return resolveBucket(user.getUsername(), user.getTariffPlan());
    }

    public Bucket resolveBucket(String key, TariffPlan tariffPlan) {
        String validKey = requireText(key);
        TariffPlan validPlan = requirePlan(tariffPlan);
        BucketEntry entry = buckets.compute(validKey, (_, existing) -> getOrCreateEntry(existing, validPlan));
        return entry.bucket();
    }

    private BucketEntry getOrCreateEntry(BucketEntry existing, TariffPlan tariffPlan) {
        if (existing != null && existing.tariffPlan() == tariffPlan) {
            return existing;
        }
        return new BucketEntry(tariffPlan, createBucket(tariffPlan));
    }

    private Bucket createBucket(TariffPlan tariffPlan) {
        long capacity = tariffPlan.getRequestsPerMinute();
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillIntervally(capacity, REFILL_PERIOD))
                .build();
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Key must not be blank");
        }
        return value;
    }

    private static TariffPlan requirePlan(TariffPlan tariffPlan) {
        if (tariffPlan == null) {
            throw new IllegalArgumentException("Tariff plan must not be null");
        }
        return tariffPlan;
    }

    private record BucketEntry(TariffPlan tariffPlan, Bucket bucket) {
    }
}
