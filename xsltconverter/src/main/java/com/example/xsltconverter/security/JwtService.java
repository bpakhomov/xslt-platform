package com.example.xsltconverter.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Component
public class JwtService {
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey secretKey;
    private final long expirationSeconds;
    private final JwtParser jwtParser;
    private final Clock clock;

    @Autowired
    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-minutes:60}") long expirationMinutes
    ) {
        this(secret, expirationMinutes, Clock.systemUTC());
    }

    JwtService(String secret, long expirationMinutes, Clock clock) {
        if (secret == null || secret.isBlank() || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalArgumentException("jwt.secret must be at least 32 bytes");
        }
        if (expirationMinutes <= 0) {
            throw new IllegalArgumentException("jwt.expiration-minutes must be positive");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = Duration.ofMinutes(expirationMinutes).toSeconds();
        this.clock = clock;
        this.jwtParser = Jwts.parser().verifyWith(secretKey)
                .clock(() -> Date.from(clock.instant())).build();
    }

    public String generateToken(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username must be not null");
        }
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(username)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    public Optional<String> extractUsername(String token) {
        try {
            Claims payload = jwtParser.parseSignedClaims(token).getPayload();
            return Optional.ofNullable(payload.getSubject())
                    .filter(subject -> !subject.isBlank());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
