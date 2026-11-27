package com.example.xsltconverter.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;

import java.time.Instant;

@Entity
@Table(name = "issued_tokens")
public class IssuedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "token_preview", nullable = false, length = 32)
    private String tokenPreview;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    @ColumnDefault("false")
    private boolean revoked;

    public IssuedToken() {
    }

    public IssuedToken(User user, TokenDetails details) {
        this.user = requireNotNull(user, "User");
        TokenDetails validDetails = requireNotNull(details, "Token details");
        this.tokenHash = requireText(validDetails.tokenHash(), "Token hash");
        this.tokenPreview = requireText(validDetails.tokenPreview(), "Token preview");
        this.issuedAt = requireNotNull(validDetails.issuedAt(), "IssuedAt");
        this.expiresAt = requireNotNull(validDetails.expiresAt(), "ExpiresAt");
        this.revoked = false;
    }

    public void revoke() {
        this.revoked = true;
    }

    public boolean isActiveAt(Instant moment) {
        return !revoked && moment != null && expiresAt.isAfter(moment);
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getTokenPreview() {
        return tokenPreview;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean isRevoked() {
        return revoked;
    }

    private static <T> T requireNotNull(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    public record TokenDetails(String tokenHash, String tokenPreview, Instant issuedAt, Instant expiresAt) {
    }
}
