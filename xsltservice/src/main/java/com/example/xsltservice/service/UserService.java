package com.example.xsltservice.service;

import com.example.xsltservice.dao.UserDao;
import com.example.xsltservice.entity.User;
import com.example.xsltservice.entity.UserStatus;
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

    public User getById(long id) {
        return transactionTemplate.execute(_ -> requireUserById(id));
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

    public List<User> findAll() {
        List<User> users = transactionTemplate.execute(_ -> userDao.findAll());
        return users != null ? users : List.of();
    }

    public User updateUsername(long id, String newUsername) {
        String validUsername = requireText(newUsername, "Username");
        RenamedUser renamed = transactionTemplate.execute(_ -> renameUserInDb(id, validUsername));
        String previousUsername = renamed.previousUsername();
        User updatedUser = renamed.user();
        if (!validUsername.equals(previousUsername)) {
            evictFromCache(previousUsername);
        }
        cacheUser(updatedUser);
        return updatedUser;
    }

    public User updatePassword(long id, String newRawPassword) {
        String validPassword = requireText(newRawPassword, "Password");
        String encodedPassword = passwordEncoder.encode(validPassword);
        User updatedUser = transactionTemplate.execute(_ -> changePasswordInDb(id, encodedPassword));
        cacheUser(updatedUser);
        return updatedUser;
    }

    public User deactivateById(long id) {
        return updateStatus(id, UserStatus.DISABLED);
    }

    public User activateById(long id) {
        return updateStatus(id, UserStatus.ACTIVE);
    }

    public void deleteById(long id) {
        String deletedUsername = transactionTemplate.execute(_ -> removeUserInDb(id));
        evictFromCache(deletedUsername);
    }

    public boolean matchesPassword(String rawPassword, String encodedPassword) {
        if (!hasText(rawPassword) || !hasText(encodedPassword)) {
            return false;
        }
        return passwordEncoder.matches(rawPassword, encodedPassword);
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

    private User findUserInDb(String username) {
        return userDao.findByUsername(username).orElse(null);
    }

    private RenamedUser renameUserInDb(long id, String newUsername) {
        User existingUser = requireUserById(id);
        String previousUsername = existingUser.getUsername();
        if (!newUsername.equals(previousUsername) && userDao.existsByUsername(newUsername)) {
            throw new IllegalArgumentException("Username already exists: " + newUsername);
        }
        existingUser.setUsername(newUsername);
        User updatedUser = userDao.update(existingUser);
        return new RenamedUser(previousUsername, updatedUser);
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

    Optional<User> readFromCache(String username) {
        return userCache != null ? userCache.get(username) : Optional.empty();
    }

    void cacheUser(User user) {
        if (userCache != null && user != null) {
            userCache.put(user);
        }
    }

    void evictFromCache(String username) {
        if (userCache != null && hasText(username)) {
            userCache.evict(username);
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

    private record RenamedUser(String previousUsername, User user) {
    }
}
