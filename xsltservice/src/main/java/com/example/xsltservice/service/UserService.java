package com.example.xsltservice.service;

import com.example.xsltservice.dao.UserDao;
import com.example.xsltservice.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.NoSuchElementException;
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

    public User getById(long id) {
        User user = transactionTemplate.execute(_ -> userDao.findById(id).orElse(null));
        if (user == null) {
            throw new NoSuchElementException("User not found: " + id);
        }
        return user;
    }

    public Optional<User> findByUsername(String username) {
        if (!hasText(username)) {
            return Optional.empty();
        }
        User user = transactionTemplate.execute(_ -> userDao.findByUsername(username).orElse(null));
        return Optional.ofNullable(user);
    }

    public List<User> findAll() {
        List<User> users = transactionTemplate.execute(_ -> userDao.findAll());
        return users != null ? users : List.of();
    }

    public User updateUsername(long id, String newUsername) {
        String validUsername = requireText(newUsername, "Username");
        return transactionTemplate.execute(_ -> {
            User user = userDao.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("User not found: " + id));
            if (!validUsername.equals(user.getUsername()) && userDao.existsByUsername(validUsername)) {
                throw new IllegalArgumentException("Username already exists: " + validUsername);
            }
            user.setUsername(validUsername);
            return userDao.update(user);
        });
    }

    public User updatePassword(long id, String newRawPassword) {
        String validPassword = requireText(newRawPassword, "Password");
        String encodedPassword = passwordEncoder.encode(validPassword);
        return transactionTemplate.execute(_ -> {
            User user = userDao.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("User not found: " + id));
            user.setPassword(encodedPassword);
            return userDao.update(user);
        });
    }

    public void deleteById(long id) {
        boolean deleted = Boolean.TRUE.equals(
                transactionTemplate.execute(_ -> userDao.deleteById(id))
        );
        if (!deleted) {
            throw new NoSuchElementException("User not found: " + id);
        }
    }

    public boolean matchesPassword(String rawPassword, String encodedPassword) {
        if (!hasText(rawPassword) || !hasText(encodedPassword)) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
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
