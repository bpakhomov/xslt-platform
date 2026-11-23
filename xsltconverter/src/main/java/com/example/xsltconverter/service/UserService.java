package com.example.xsltconverter.service;

import com.example.xsltconverter.dao.UserDao;
import com.example.xsltconverter.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

@Service
public class UserService {
    private final UserDao userDao;
    private final TransactionTemplate transactionTemplate;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserService(
            UserDao userDao,
            TransactionTemplate transactionTemplate,
            PasswordEncoder passwordEncoder
    ) {
        this.userDao = userDao;
        this.transactionTemplate = transactionTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    public User createUser(String username, String rawPassword) {
        String validUsername = requireText(username, "Username");
        String validPassword = requireText(rawPassword, "Password");
        String encodedPassword = passwordEncoder.encode(validPassword);

        return transactionTemplate.execute(_ -> {
            if (userDao.existsByUsername(validUsername)) {
                throw new IllegalArgumentException("Username already exists: " + validUsername);
            }
            return userDao.save(new User(validUsername, encodedPassword));
        });
    }

    public void ensureUserExists(String username, String rawPassword) {
        if (!hasText(username) || !hasText(rawPassword)) {
            return;
        }
        String encodedPassword = passwordEncoder.encode(rawPassword);
        transactionTemplate.executeWithoutResult(_ -> {
            if (!userDao.existsByUsername(username)) {
                userDao.save(new User(username, encodedPassword));
            }
        });
    }

    public Optional<User> findByUsername(String username) {
        if (!hasText(username)) {
            return Optional.empty();
        }
        User user = transactionTemplate.execute(_ -> userDao.findByUsername(username).orElse(null));
        return Optional.ofNullable(user);
    }

    private static String requireText(String value, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
