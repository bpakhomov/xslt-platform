package com.example.xsltconverter.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

@Component
public class JwtService {

    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final String HMAC_ALG = "HmacSHA256";
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final SecretKeySpec secretKey;
    private final long expirationSeconds;
    private final Clock clock;

    @Autowired
    public JwtService(
            ObjectMapper objectMapper,
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-minutes:60}") long expirationMinutes
    ) {
        this(objectMapper, secret, expirationMinutes, Clock.systemUTC());
    }

    JwtService(ObjectMapper objectMapper, String secret, long expirationMinutes, Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("jwt.secret must be at least 32 bytes");
        }
        if (expirationMinutes <= 0) {
            throw new IllegalArgumentException("jwt.expiration-minutes must be positive");
        }
        this.objectMapper = objectMapper;
        this.secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALG);
        this.expirationSeconds = expirationMinutes * 60;
        this.clock = clock;
    }

    public String generateToken(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must not be blank");
        }
        long now = clock.instant().getEpochSecond();
        String header = encode(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
        String payload = encode(objectMapper.writeValueAsBytes(Map.of(
                "sub", username,
                "iat", now,
                "exp", now + expirationSeconds
        )));
        String signingInput = header + "." + payload;
        return signingInput + "." + encode(sign(signingInput));
    }

    public Optional<String> extractUsername(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            return Optional.empty();
        }
        try {
            byte[] expectedSig = sign(parts[0] + "." + parts[1]);
            byte[] actualSig = URL_DECODER.decode(parts[2]);
            if (!MessageDigest.isEqual(expectedSig, actualSig)) {
                return Optional.empty();
            }
            JsonNode header = objectMapper.readTree(URL_DECODER.decode(parts[0]));
            if (!"HS256".equals(header.path("alg").asString(""))) {
                return Optional.empty();
            }
            JsonNode payload = objectMapper.readTree(URL_DECODER.decode(parts[1]));
            long exp = payload.path("exp").asLong(0);
            String subject = payload.path("sub").asString("");
            if (subject.isBlank() || clock.instant().getEpochSecond() >= exp) {
                return Optional.empty();
            }
            return Optional.of(subject);
        } catch (IllegalArgumentException | JacksonException e) {
            return Optional.empty();
        }
    }

    private byte[] sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(secretKey);
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    private static String encode(byte[] bytes) {
        return URL_ENCODER.encodeToString(bytes);
    }
}
