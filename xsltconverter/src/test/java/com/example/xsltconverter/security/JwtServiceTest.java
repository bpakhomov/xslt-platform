package com.example.xsltconverter.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-at-least-32-bytes-long";

    private ObjectMapper objectMapper;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        jwtService = new JwtService(objectMapper, SECRET, 60);
    }

    @Nested
    class Constructor {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "short")
        void throwsWhenSecretIsInvalid(String secret) {
            assertThrows(IllegalArgumentException.class, () -> new JwtService(objectMapper, secret, 60));
        }

        @ParameterizedTest
        @ValueSource(longs = {0, -5})
        void throwsWhenExpirationIsNotPositive(long minutes) {
            assertThrows(IllegalArgumentException.class, () -> new JwtService(objectMapper, SECRET, minutes));
        }
    }

    @Nested
    class GenerateToken {

        @Test
        void generatesValidTokenForUsername() {
            String token = jwtService.generateToken("alice");

            assertEquals(3, token.split("\\.").length);
            assertEquals("alice", jwtService.extractUsername(token).orElseThrow());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void throwsWhenUsernameIsBlank(String username) {
            assertThrows(IllegalArgumentException.class, () -> jwtService.generateToken(username));
        }
    }

    @Nested
    class ExtractUsername {

        @Test
        void returnsUsernameForValidToken() {
            String token = jwtService.generateToken("alice");

            assertEquals("alice", jwtService.extractUsername(token).orElseThrow());
        }

        @Test
        void returnsEmptyWhenSignatureIsTampered() {
            String token = jwtService.generateToken("alice");
            String[] parts = token.split("\\.");
            char tamperedFirst = parts[2].charAt(0) == 'A' ? 'B' : 'A';
            String tampered = parts[0] + "." + parts[1] + "." + tamperedFirst + parts[2].substring(1);

            assertTrue(jwtService.extractUsername(tampered).isEmpty());
        }

        @Test
        void returnsEmptyWhenTokenIsExpired() {
            Instant issuedAt = Instant.parse("2026-11-23T10:00:00Z");
            JwtService issuer = new JwtService(objectMapper, SECRET, 1, Clock.fixed(issuedAt, ZoneOffset.UTC));
            JwtService validator = new JwtService(objectMapper, SECRET, 1, Clock.fixed(issuedAt.plusSeconds(61), ZoneOffset.UTC));

            String expiredToken = issuer.generateToken("alice");

            assertTrue(validator.extractUsername(expiredToken).isEmpty());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "not-a-jwt", "a.b", "a.b.c"})
        void returnsEmptyWhenTokenIsMalformed(String token) {
            assertTrue(jwtService.extractUsername(token).isEmpty());
        }
    }
}
