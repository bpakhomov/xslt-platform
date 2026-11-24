package com.example.xsltconverter.service;

import com.example.xsltconverter.dao.UserDao;
import com.example.xsltconverter.entity.User;
import com.example.xsltconverter.entity.UserStatus;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final UserCache userCache;

    public UserService(
            UserDao userDao,
            TransactionTemplate transactionTemplate,
            PasswordEncoder passwordEncoder
    ) {
        this(userDao, transactionTemplate, passwordEncoder, null);
    }

    @Autowired
    public UserService(
            UserDao userDao,
            TransactionTemplate transactionTemplate,
            PasswordEncoder passwordEncoder,
            UserCache userCache
    ) {
        this.userDao = userDao;
        this.transactionTemplate = transactionTemplate;
        this.passwordEncoder = passwordEncoder;
        this.userCache = userCache;
    }

    public User createUser(String username, String rawPassword) {
        String validUsername = requireText(username, "Username");
        String validPassword = requireText(rawPassword, "Password");
        String encodedPassword = passwordEncoder.encode(validPassword);

        User createdUser = transactionTemplate.execute(_ -> persistNewUserInDb(validUsername, encodedPassword));
        cacheUser(createdUser);
        return createdUser;
    }

    public void ensureUserExists(String username, String rawPassword) {
        if (!hasText(username) || !hasText(rawPassword)) {
            return;
        }
        String encodedPassword = passwordEncoder.encode(rawPassword);
        User createdUser = transactionTemplate.execute(_ -> createIfMissingInDb(username, encodedPassword));
        cacheUser(createdUser);
    }

    public Optional<User> findByUsername(String username) {
        if (!hasText(username)) {
            return Optional.empty();
        }
        Optional<User> cachedUser = readFromCache(username);
        if (cachedUser.isPresent()) {
            return cachedUser;
        }
        User dbUser = transactionTemplate.execute(_ -> findUserInDb(username));
        if (dbUser == null) {
            return Optional.empty();
        }
        cacheUser(dbUser);
        return Optional.of(dbUser);
    }

    public User deactivateById(long id) {
        return updateStatus(id, UserStatus.DISABLED);
    }

    public User activateById(long id) {
        return updateStatus(id, UserStatus.ACTIVE);
    }

    private User updateStatus(long id, UserStatus targetStatus) {
        User updatedUser = transactionTemplate.execute(_ -> changeStatusInDb(id, targetStatus));
        cacheUser(updatedUser);
        return updatedUser;
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

    private User findUserInDb(String username) {
        return userDao.findByUsername(username).orElse(null);
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

    Optional<User> readFromCache(String username) {
        return userCache != null ? userCache.get(username) : Optional.empty();
    }

    void cacheUser(User user) {
        if (userCache != null && user != null) {
            userCache.put(user);
        }
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
