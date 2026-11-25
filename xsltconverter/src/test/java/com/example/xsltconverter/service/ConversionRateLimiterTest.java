package com.example.xsltconverter.service;

import com.example.xsltconverter.exception.RateLimitExceededException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConversionRateLimiterTest {

    @Nested
    class Constructor {

        @ParameterizedTest
        @ValueSource(longs = {0, -1})
        void throwsWhenRequestsPerSecondIsNotPositive(long requestsPerSecond) {
            assertThrows(IllegalArgumentException.class, () -> new ConversionRateLimiter(requestsPerSecond));
        }
    }

    @Nested
    class AcquirePermit {

        @Test
        void allowsRequestsWithinConfiguredLimit() {
            ConversionRateLimiter limiter = new ConversionRateLimiter(2);

            assertDoesNotThrow(limiter::acquirePermit);
            assertDoesNotThrow(limiter::acquirePermit);
        }

        @Test
        void throwsRateLimitExceededWhenQuotaExhausted() {
            ConversionRateLimiter limiter = new ConversionRateLimiter(1);
            limiter.acquirePermit();

            RateLimitExceededException exception =
                    assertThrows(RateLimitExceededException.class, limiter::acquirePermit);

            assertTrue(exception.getRetryAfterSeconds() >= 1L);
        }
    }
}
