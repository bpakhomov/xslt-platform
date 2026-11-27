package com.example.xsltconverter.service;

import com.example.xsltconverter.dao.IssuedTokenDao;
import com.example.xsltconverter.entity.IssuedToken;
import com.example.xsltconverter.entity.User;
import com.example.xsltconverter.security.JwtService;
import com.example.xsltconverter.security.TokenMasker;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class IssuedTokenService {

    private final IssuedTokenDao issuedTokenDao;
    private final TransactionTemplate transactionTemplate;
    private final JwtService jwtService;

    public IssuedTokenService(IssuedTokenDao issuedTokenDao, TransactionTemplate transactionTemplate, JwtService jwtService) {
        this.issuedTokenDao = issuedTokenDao;
        this.transactionTemplate = transactionTemplate;
        this.jwtService = jwtService;
    }

    @Transactional
    public String issueToken(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null");
        }

        JwtService.GeneratedToken tokenInfo = jwtService.generateTokenInfo(user.getUsername());

        IssuedToken.TokenDetails tokenDetails = new IssuedToken.TokenDetails(
                hashToken(tokenInfo.token()),
                TokenMasker.maskToken(tokenInfo.token()),
                tokenInfo.issuedAt(),
                tokenInfo.expiresAt()
        );

        issuedTokenDao.save(new IssuedToken(user, tokenDetails));

        return tokenInfo.token();
    }

    @Cacheable(
            condition = "#token != null && !#token.isBlank()",
            key = "#root.target.hashToken(#token)",
            unless = "!#result",
            value = "tokens"
    )
    public boolean isTokenActive(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Optional<IssuedToken> issuedToken = transactionTemplate.execute(
                _ -> issuedTokenDao.findByTokenHash(hashToken(token)));
        if (issuedToken == null || issuedToken.isEmpty()) {
            return false;
        }
        return issuedToken.filter(value -> value.isActiveAt(Instant.now())).isPresent();
    }

    @CacheEvict(
            key = "#root.target.hashToken(#token)",
            value = "tokens",
            condition = "#token != null && !#token.isBlank()"
    )
    @Transactional
    public void revokeToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        issuedTokenDao.revokeByTokenHash(hashToken(token));
    }

    public String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
