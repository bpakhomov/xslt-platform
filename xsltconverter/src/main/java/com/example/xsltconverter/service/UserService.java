package com.example.xsltconverter.service;

import com.example.xsltconverter.dao.UserDao;
import com.example.xsltconverter.entity.User;
import com.example.xsltconverter.entity.UserStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class UserService {
    private final UserDao userDao;
    private final TransactionTemplate transactionTemplate;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserDao userDao,
            TransactionTemplate transactionTemplate,
            PasswordEncoder passwordEncoder, IssuedTokenService issuedTokenService
    ) {
        this.userDao = userDao;
        this.transactionTemplate = transactionTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    public User createUser(String username, String rawPassword) {
        String validUsername = requireText(username, "Username");
        String validPassword = requireText(rawPassword, "Password");
        String encodedPassword = passwordEncoder.encode(validPassword);

        return transactionTemplate.execute(_ -> persistNewUserInDb(validUsername, encodedPassword));
    }

    public void ensureUserExists(String username, String rawPassword) {
        if (!hasText(username) || !hasText(rawPassword)) {
            return;
        }
        String encodedPassword = passwordEncoder.encode(rawPassword);
        transactionTemplate.execute(_ -> createIfMissingInDb(username, encodedPassword));
    }

    public Optional<User> findByUsername(String username) {
        if (!hasText(username)) {
            return Optional.empty();
        }
        return transactionTemplate.execute(_ -> userDao.findByUsername(username));
    }

    public User deactivateById(long id) {
        return updateStatus(id, UserStatus.DISABLED);
    }

    public User activateById(long id) {
        return updateStatus(id, UserStatus.ACTIVE);
    }

    private User updateStatus(long id, UserStatus targetStatus) {
        return transactionTemplate.execute(_ -> changeStatusInDb(id, targetStatus));
    }

    private User persistNewUserInDb(String username, String encodedPassword) {
        if (userDao.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }
        return userDao.save(new User(username, encodedPassword));
    }

    private User createIfMissingInDb(String username, String encodedPassword) {
        if (userDao.existsByUsername(username)) {
            return null;
        }
        return userDao.save(new User(username, encodedPassword));
    }

    private User changeStatusInDb(long id, UserStatus targetStatus) {
        User existingUser = requireUserById(id);
        existingUser.setStatus(targetStatus);
        return userDao.update(existingUser);
    }

    private User requireUserById(long id) {
        return userDao.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + id));
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
