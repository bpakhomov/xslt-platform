package com.example.xsltservice.service;

import com.example.xsltservice.entity.TariffPlan;
import com.example.xsltservice.entity.User;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PricingPlanServiceTest {

    private PricingPlanService pricingPlanService;

    @BeforeEach
    void setUp() {
        pricingPlanService = new PricingPlanService();
    }

    @Nested
    class ResolveBucket {

        @Test
        void createsBucketWithLimitFromUserTariffPlan() {
            User user = new User("alice", "hash");
            user.setTariffPlan(TariffPlan.BASIC);

            Bucket bucket = pricingPlanService.resolveBucket(user);

            assertEquals(30L, bucket.getAvailableTokens());
        }

        @Test
        void reusesSameBucketForSameUserAndPlan() {
            User user = new User("alice", "hash");

            Bucket first = pricingPlanService.resolveBucket(user);
            Bucket second = pricingPlanService.resolveBucket(user);

            assertSame(first, second);
            assertEquals(12L, first.getAvailableTokens());
        }

        @Test
        void replacesBucketWhenUserTariffPlanChanges() {
            User user = new User("alice", "hash");
            Bucket initialBucket = pricingPlanService.resolveBucket(user);

            user.setTariffPlan(TariffPlan.PROFESSIONAL);
            Bucket upgradedBucket = pricingPlanService.resolveBucket(user);

            assertNotSame(initialBucket, upgradedBucket);
            assertEquals(60L, upgradedBucket.getAvailableTokens());
        }

        @Test
        void throwsWhenUserIsNull() {
            assertThrows(IllegalArgumentException.class, () -> pricingPlanService.resolveBucket(null));
        }

        @Test
        void throwsWhenTariffPlanIsNull() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> pricingPlanService.resolveBucket("alice", null)
            );
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void throwsWhenKeyIsBlank(String key) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> pricingPlanService.resolveBucket(key, TariffPlan.FREE)
            );
        }
    }
}
