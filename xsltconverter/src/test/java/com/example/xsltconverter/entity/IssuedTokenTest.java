package com.example.xsltconverter.entity;

import com.example.xsltconverter.entity.IssuedToken.TokenDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IssuedTokenTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-11-26T10:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-11-26T11:00:00Z");

    private User user;
    private TokenDetails validDetails;

    @BeforeEach
    void setUp() {
        user = new User("alice", "hashed");
        validDetails = new TokenDetails("a".repeat(64), "...12345678", ISSUED_AT, EXPIRES_AT);
    }

    @Nested
    class Constructor {

        @Test
        void createsActiveTokenWhenArgumentsAreValid() {
            IssuedToken token = new IssuedToken(user, validDetails);

            assertEquals(user, token.getUser());
            assertEquals(validDetails.tokenHash(), token.getTokenHash());
            assertEquals("...12345678", token.getTokenPreview());
            assertEquals(ISSUED_AT, token.getIssuedAt());
            assertEquals(EXPIRES_AT, token.getExpiresAt());
            assertFalse(token.isRevoked());
        }

        @Test
        void throwsWhenUserIsNull() {
            assertThrows(IllegalArgumentException.class, () -> new IssuedToken(null, validDetails));
        }

        @Test
        void throwsWhenDetailsIsNull() {
            assertThrows(IllegalArgumentException.class, () -> new IssuedToken(user, null));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void throwsWhenTokenHashIsBlank(String tokenHash) {
            TokenDetails details = new TokenDetails(tokenHash, "...12345678", ISSUED_AT, EXPIRES_AT);

            assertThrows(IllegalArgumentException.class, () -> new IssuedToken(user, details));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void throwsWhenTokenPreviewIsBlank(String tokenPreview) {
            TokenDetails details = new TokenDetails("a".repeat(64), tokenPreview, ISSUED_AT, EXPIRES_AT);

            assertThrows(IllegalArgumentException.class, () -> new IssuedToken(user, details));
        }
    }

    @Nested
    class IsActiveAt {

        @Test
        void returnsTrueBeforeExpirationWhenNotRevoked() {
            IssuedToken token = new IssuedToken(user, validDetails);

            assertTrue(token.isActiveAt(ISSUED_AT.plusSeconds(60)));
        }

        @Test
        void returnsFalseAtOrAfterExpiration() {
            IssuedToken token = new IssuedToken(user, validDetails);

            assertFalse(token.isActiveAt(EXPIRES_AT));
            assertFalse(token.isActiveAt(EXPIRES_AT.plusSeconds(1)));
        }

        @Test
        void returnsFalseWhenMomentIsNull() {
            IssuedToken token = new IssuedToken(user, validDetails);

            assertFalse(token.isActiveAt(null));
        }

        @Test
        void returnsFalseWhenRevoked() {
            IssuedToken token = new IssuedToken(user, validDetails);
            token.revoke();

            assertTrue(token.isRevoked());
            assertFalse(token.isActiveAt(ISSUED_AT.plusSeconds(60)));
        }
    }
}
