package com.example.xsltconverter.security;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenMaskerTest {

    @Nested
    class MaskToken {

        @Test
        void keepsOnlyLastEightCharactersForLongToken() {
            assertEquals("...12345678", TokenMasker.maskToken("eyJhbGciOiJIUzI1NiJ9.12345678"));
        }

        @Test
        void trimsWhitespaceBeforeMasking() {
            assertEquals("...12345678", TokenMasker.maskToken("  abcdef12345678  "));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "a", "12345678"})
        void returnsMaskPlaceholderForBlankOrShortToken(String token) {
            assertEquals("***", TokenMasker.maskToken(token));
        }
    }
}
