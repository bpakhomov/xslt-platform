package com.example.xsltconverter.service;

import com.example.xsltconverter.exception.RateLimitExceededException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ConversionRateLimiter {

    private static final Duration REFILL_PERIOD = Duration.ofSeconds(1);
    private static final long NANOSECONDS_IN_SECOND = 1_000_000_000L;
    private static final long MIN_RETRY_AFTER_SECONDS = 1L;
    private static final long TOKENS_PER_REQUEST = 1L;
    private static final String RATE_LIMIT_EXCEEDED_MESSAGE = "Too many requests to /xml2format";

    private final Bucket bucket;

    public ConversionRateLimiter(@Value("${rate-limit.xml2format.requests-per-second:1}") long requestsPerSecond) {
        if (requestsPerSecond <= 0) {
            throw new IllegalArgumentException("Rate limit requestsPerSecond must be positive");
        }
        this.bucket = Bucket.builder()
                .addLimit(limit -> limit.capacity(requestsPerSecond)
                        .refillIntervally(requestsPerSecond, REFILL_PERIOD))
                .build();
    }

    public void acquirePermit() {
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(TOKENS_PER_REQUEST);
        if (probe.isConsumed()) {
            return;
        }
        long waitSeconds = calculateRetryAfterSeconds(probe);
        throw new RateLimitExceededException(waitSeconds, RATE_LIMIT_EXCEEDED_MESSAGE);
    }

    private long calculateRetryAfterSeconds(ConsumptionProbe probe) {
        long nanosToWait = probe.getNanosToWaitForRefill();
        long roundedUpSeconds = (nanosToWait + NANOSECONDS_IN_SECOND - 1L) / NANOSECONDS_IN_SECOND;
        return Math.max(MIN_RETRY_AFTER_SECONDS, roundedUpSeconds);
    }
}
