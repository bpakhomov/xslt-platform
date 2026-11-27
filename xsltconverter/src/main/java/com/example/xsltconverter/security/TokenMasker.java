package com.example.xsltconverter.security;

public final class TokenMasker {

    private static final String MASKED_PLACEHOLDER = "***";
    private static final int VISIBLE_TAIL_LENGTH = 8;

    private TokenMasker() {
    }

    public static String maskToken(String token) {
        if (token == null || token.isBlank()) {
            return MASKED_PLACEHOLDER;
        }
        String trimmed = token.trim();
        if (trimmed.length() <= VISIBLE_TAIL_LENGTH) {
            return MASKED_PLACEHOLDER;
        }
        return "..." + trimmed.substring(trimmed.length() - VISIBLE_TAIL_LENGTH);
    }
}
