package com.example.xsltservice.service;

import org.springframework.http.MediaType;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

class ConverterAuthClient {

    private static final long EXPIRY_SKEW_SECONDS = 30;
    private static final long FALLBACK_TTL_SECONDS = 300;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI loginUri;
    private final String username;
    private final String password;

    private volatile String cachedToken;
    private volatile Instant expiresAt = Instant.EPOCH;

    ConverterAuthClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String baseUrl,
            String username,
            String password
    ) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.loginUri = URI.create(baseUrl + "/auth/login");
        this.username = username;
        this.password = password;
    }

    String getToken() throws IOException, InterruptedException {
        if (hasValidCachedToken()) {
            return cachedToken;
        }
        return refreshToken();
    }

    synchronized String refreshToken() throws IOException, InterruptedException {
        if (hasValidCachedToken()) {
            return cachedToken;
        }
        return fetchAndCacheToken();
    }

    synchronized String forceRefreshToken() throws IOException, InterruptedException {
        return fetchAndCacheToken();
    }

    private String fetchAndCacheToken() throws IOException, InterruptedException {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "password", password
        ));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(loginUri)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException(
                    "xsltconverter /auth/login returned HTTP " + response.statusCode() + ": " + response.body()
            );
        }

        String token = objectMapper.readTree(response.body()).path("token").asString("");
        if (token.isBlank()) {
            throw new IllegalStateException("xsltconverter /auth/login returned empty token");
        }
        this.expiresAt = parseExpiration(token);
        this.cachedToken = token;
        return token;
    }

    private boolean hasValidCachedToken() {
        return cachedToken != null && Instant.now().plusSeconds(EXPIRY_SKEW_SECONDS).isBefore(expiresAt);
    }

    private Instant parseExpiration(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length == 3) {
                JsonNode payload = objectMapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
                long exp = payload.path("exp").asLong(0);
                if (exp > 0) {
                    return Instant.ofEpochSecond(exp);
                }
            }
        } catch (IllegalArgumentException | JacksonException ignored) {
            // Fall back to short TTL when token payload cannot be decoded.
        }
        return Instant.now().plusSeconds(FALLBACK_TTL_SECONDS);
    }
}
