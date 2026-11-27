package com.example.xsltservice.service;

import com.example.xsltservice.dao.UserDao;
import com.example.xsltservice.entity.User;
import com.example.xsltservice.entity.UserStatus;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class UserService {

    private static final String USERS_CACHE = "users";

    private final UserDao userDao;
    private final TransactionTemplate transactionTemplate;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserDao userDao,
            TransactionTemplate transactionTemplate,
            PasswordEncoder passwordEncoder
    ) {
        this.userDao = userDao;
        this.transactionTemplate = transactionTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @CachePut(value = USERS_CACHE, key = "#result.username")
    public User createUser(String username, String rawPassword) {
        String validUsername = requireText(username, "Username");
        String validPassword = requireText(rawPassword, "Password");
        String encodedPassword = passwordEncoder.encode(validPassword);

        return transactionTemplate.execute(_ -> persistNewUserInDb(validUsername, encodedPassword));
    }

    public User getById(long id) {
        return transactionTemplate.execute(_ -> requireUserById(id));
    }

    @Cacheable(
            value = USERS_CACHE,
            key = "#username",
            condition = "#username != null && !#username.isBlank()"
    )
    public Optional<User> findByUsername(String username) {
        if (!hasText(username)) {
            return Optional.empty();
        }
        return transactionTemplate.execute(_ -> userDao.findByUsername(username));
    }

    public List<User> findAll() {
        List<User> users = transactionTemplate.execute(_ -> userDao.findAll());
        return users != null ? users : List.of();
    }

    @CacheEvict(value = USERS_CACHE, key = "#result", condition = "#result != null && !#result.isBlank()")
    public String updateUsername(long id, String newUsername) {
        String validUsername = requireText(newUsername, "Username");
        return transactionTemplate.execute(_ -> renameUserInDb(id, validUsername));
    }

    @CachePut(value = USERS_CACHE, key = "#result.username")
    public User updatePassword(long id, String newRawPassword) {
        String validPassword = requireText(newRawPassword, "Password");
        String encodedPassword = passwordEncoder.encode(validPassword);
        return transactionTemplate.execute(_ -> changePasswordInDb(id, encodedPassword));
    }

    @CachePut(value = USERS_CACHE, key = "#result.username")
    public User deactivateById(long id) {
        return updateStatus(id, UserStatus.DISABLED);
    }

    @CachePut(value = USERS_CACHE, key = "#result.username")
    public User activateById(long id) {
        return updateStatus(id, UserStatus.ACTIVE);
    }

    @CacheEvict(value = USERS_CACHE, key = "#result", condition = "#result != null && !#result.isBlank()")
    public String deleteById(long id) {
        return transactionTemplate.execute(_ -> removeUserInDb(id));
    }

    public boolean matchesPassword(String rawPassword, String encodedPassword) {
        if (!hasText(rawPassword) || !hasText(encodedPassword)) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
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

    private String renameUserInDb(long id, String newUsername) {
        User existingUser = requireUserById(id);
        String previousUsername = existingUser.getUsername();
        if (!newUsername.equals(previousUsername) && userDao.existsByUsername(newUsername)) {
            throw new IllegalArgumentException("Username already exists: " + newUsername);
        }
        existingUser.setUsername(newUsername);
        userDao.update(existingUser);
        return previousUsername;
    }

    private User changePasswordInDb(long id, String encodedPassword) {
        User existingUser = requireUserById(id);
        existingUser.setPassword(encodedPassword);
        return userDao.update(existingUser);
    }

    private User changeStatusInDb(long id, UserStatus targetStatus) {
        User existingUser = requireUserById(id);
        existingUser.setStatus(targetStatus);
        return userDao.update(existingUser);
    }

    private String removeUserInDb(long id) {
        User existingUser = requireUserById(id);
        if (!userDao.deleteById(id)) {
            throw new NoSuchElementException("User not found: " + id);
        }
        return existingUser.getUsername();
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
