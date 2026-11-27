package com.example.xsltconverter.dao;

import com.example.xsltconverter.entity.IssuedToken;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class IssuedTokenDao {

    private final SessionFactory sessionFactory;

    @Autowired
    public IssuedTokenDao(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public IssuedToken save(IssuedToken token) {
        if (token == null) {
            throw new IllegalArgumentException("IssuedToken must not be null");
        }
        sessionFactory.getCurrentSession().persist(token);
        return token;
    }

    public Optional<IssuedToken> findByTokenHash(String tokenHash) {
        if (!hasText(tokenHash)) {
            return Optional.empty();
        }
        return sessionFactory.getCurrentSession()
                .createSelectionQuery("from IssuedToken t where t.tokenHash = :tokenHash", IssuedToken.class)
                .setParameter("tokenHash", tokenHash)
                .uniqueResultOptional();
    }

    public List<IssuedToken> findByUserId(long userId) {
        return sessionFactory.getCurrentSession()
                .createSelectionQuery(
                        "from IssuedToken t where t.user.id = :userId order by t.issuedAt desc, t.id desc",
                        IssuedToken.class
                )
                .setParameter("userId", userId)
                .getResultList();
    }

    public long countByUserId(long userId) {
        return sessionFactory.getCurrentSession()
                .createSelectionQuery("select count(t.id) from IssuedToken t where t.user.id = :userId", Long.class)
                .setParameter("userId", userId)
                .getSingleResult();
    }

    public long countActiveByUserId(long userId, Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("Instant must not be null");
        }
        return sessionFactory.getCurrentSession()
                .createSelectionQuery(
                        "select count(t.id) from IssuedToken t " +
                                "where t.user.id = :userId and t.revoked = false and t.expiresAt > :now",
                        Long.class
                )
                .setParameter("userId", userId)
                .setParameter("now", now)
                .getSingleResult();
    }

    public boolean revokeByTokenHash(String tokenHash) {
        if (!hasText(tokenHash)) {
            return false;
        }
        int updated = sessionFactory.getCurrentSession()
                .createMutationQuery("update IssuedToken t set t.revoked = true where t.tokenHash = :tokenHash and t.revoked = false")
                .setParameter("tokenHash", tokenHash)
                .executeUpdate();
        return updated > 0;
    }

    public int revokeAllByUserId(long userId) {
        return sessionFactory.getCurrentSession()
                .createMutationQuery("update IssuedToken t set t.revoked = true where t.user.id = :userId and t.revoked = false")
                .setParameter("userId", userId)
                .executeUpdate();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
