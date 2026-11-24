package com.example.xsltconverter.service;

import com.example.xsltconverter.dao.UserDao;
import com.example.xsltconverter.entity.User;
import com.example.xsltconverter.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private UserDao userDao;
    private UserCache userCache;
    private TransactionTemplate transactionTemplate;
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userDao = mock(UserDao.class);
        userCache = mock(UserCache.class);
        transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(
                userDao,
                transactionTemplate,
                passwordEncoder,
                userCache
        );
    }

    @Nested
    class CreateUser {

        @Test
        void hashesPasswordSavesAndCachesUser() {
            when(userDao.existsByUsername("alice")).thenReturn(false);
            when(userDao.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            User created = userService.createUser("alice", "secret123");

            assertEquals("alice", created.getUsername());
            assertNotEquals("secret123", created.getPassword());
            assertTrue(passwordEncoder.matches("secret123", created.getPassword()));
            verify(userDao).save(any(User.class));
            verify(userCache).put(created);
        }

        @Test
        void throwsWhenUsernameAlreadyExists() {
            when(userDao.existsByUsername("alice")).thenReturn(true);

            assertThrows(IllegalArgumentException.class, () -> userService.createUser("alice", "secret123"));
            verify(userDao, never()).save(any());
            verifyNoInteractions(userCache);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void throwsWhenUsernameIsBlank(String username) {
            assertThrows(IllegalArgumentException.class, () -> userService.createUser(username, "secret123"));
            verifyNoInteractions(userDao, userCache);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void throwsWhenPasswordIsBlank(String password) {
            assertThrows(IllegalArgumentException.class, () -> userService.createUser("alice", password));
            verifyNoInteractions(userDao, userCache);
        }
    }

    @Nested
    class EnsureUserExists {

        @Test
        void createsAndCachesUserWhenMissing() {
            when(userDao.existsByUsername("xsltservice")).thenReturn(false);
            when(userDao.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.ensureUserExists("xsltservice", "secret123");

            verify(userDao).save(any(User.class));
            verify(userCache).put(any(User.class));
        }

        @Test
        void skipsCreationWhenUserAlreadyExists() {
            when(userDao.existsByUsername("xsltservice")).thenReturn(true);

            userService.ensureUserExists("xsltservice", "secret123");

            verify(userDao, never()).save(any());
            verifyNoInteractions(userCache);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void ignoresBlankUsername(String username) {
            userService.ensureUserExists(username, "secret123");

            verifyNoInteractions(userDao, userCache);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void ignoresBlankPassword(String password) {
            userService.ensureUserExists("xsltservice", password);

            verifyNoInteractions(userDao, userCache);
        }
    }

    @Nested
    class FindByUsername {

        @Test
        void returnsCachedUserWithoutDatabaseLookup() {
            User cached = new User("alice", "hashed");
            cached.setId(5L);
            when(userCache.get("alice")).thenReturn(Optional.of(cached));

            Optional<User> found = userService.findByUsername("alice");

            assertTrue(found.isPresent());
            assertEquals(5L, found.get().getId());
            assertEquals("alice", found.get().getUsername());
            verifyNoInteractions(userDao);
            verify(userCache, never()).put(any());
        }

        @Test
        void loadsFromDatabaseAndCachesOnCacheMiss() {
            User dbUser = new User("alice", "hashed");
            dbUser.setId(5L);
            when(userCache.get("alice")).thenReturn(Optional.empty());
            when(userDao.findByUsername("alice")).thenReturn(Optional.of(dbUser));

            Optional<User> found = userService.findByUsername("alice");

            assertTrue(found.isPresent());
            assertEquals(5L, found.get().getId());
            verify(userCache).put(dbUser);
        }

        @Test
        void loadsFromDatabaseWhenCacheIsNull() {
            UserService serviceWithoutCache = new UserService(userDao, transactionTemplate, passwordEncoder);
            User dbUser = new User("alice", "hashed");
            dbUser.setId(5L);
            when(userDao.findByUsername("alice")).thenReturn(Optional.of(dbUser));

            Optional<User> found = serviceWithoutCache.findByUsername("alice");

            assertTrue(found.isPresent());
            assertEquals(5L, found.get().getId());
            verify(userDao).findByUsername("alice");
        }

        @Test
        void returnsEmptyWhenNotFoundAnywhere() {
            when(userCache.get("missing")).thenReturn(Optional.empty());
            when(userDao.findByUsername("missing")).thenReturn(Optional.empty());

            assertTrue(userService.findByUsername("missing").isEmpty());
            verify(userCache, never()).put(any());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void returnsEmptyWhenUsernameIsBlank(String username) {
            assertTrue(userService.findByUsername(username).isEmpty());
            verifyNoInteractions(userDao, userCache);
        }
    }

    @Nested
    class DeactivateById {

        @Test
        void deactivatesUserAndOverwritesCache() {
            User user = new User("alice", "hashed");
            user.setId(1L);
            when(userDao.findById(1L)).thenReturn(Optional.of(user));
            when(userDao.update(user)).thenReturn(user);

            User result = userService.deactivateById(1L);

            assertEquals(UserStatus.DISABLED, result.getStatus());
            assertFalse(result.isActive());
            verify(userDao).update(user);
            verify(userCache).put(result);
        }

        @Test
        void throwsWhenUserNotFound() {
            when(userDao.findById(1L)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> userService.deactivateById(1L));
            verify(userDao, never()).update(any());
            verifyNoInteractions(userCache);
        }
    }

    @Nested
    class ActivateById {

        @Test
        void activatesUserAndOverwritesCache() {
            User user = new User("alice", "hashed");
            user.setId(1L);
            user.setStatus(UserStatus.DISABLED);
            when(userDao.findById(1L)).thenReturn(Optional.of(user));
            when(userDao.update(user)).thenReturn(user);

            User result = userService.activateById(1L);

            assertEquals(UserStatus.ACTIVE, result.getStatus());
            assertTrue(result.isActive());
            verify(userDao).update(user);
            verify(userCache).put(result);
        }

        @Test
        void throwsWhenUserNotFound() {
            when(userDao.findById(1L)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> userService.activateById(1L));
            verify(userDao, never()).update(any());
            verifyNoInteractions(userCache);
        }
    }

    @Nested
    class ReadFromCache {

        @Test
        void returnsCachedUserWhenPresent() {
            User cached = new User("alice", "hashed");
            when(userCache.get("alice")).thenReturn(Optional.of(cached));

            Optional<User> result = userService.readFromCache("alice");

            assertTrue(result.isPresent());
            assertEquals("alice", result.get().getUsername());
            verify(userCache).get("alice");
        }

        @Test
        void returnsEmptyWhenCacheMiss() {
            when(userCache.get("alice")).thenReturn(Optional.empty());

            Optional<User> result = userService.readFromCache("alice");

            assertTrue(result.isEmpty());
            verify(userCache).get("alice");
        }

        @Test
        void returnsEmptyWhenCacheIsNull() {
            UserService serviceWithoutCache = new UserService(userDao, transactionTemplate, passwordEncoder);

            Optional<User> result = serviceWithoutCache.readFromCache("alice");

            assertTrue(result.isEmpty());
            verifyNoInteractions(userCache);
        }
    }

    @Nested
    class CacheUser {

        @Test
        void cachesUserWhenCacheAndUserArePresent() {
            User user = new User("alice", "hashed");

            userService.cacheUser(user);

            verify(userCache).put(user);
        }

        @Test
        void doesNothingWhenUserIsNull() {
            userService.cacheUser(null);

            verifyNoInteractions(userCache);
        }

        @Test
        void doesNothingWhenCacheIsNull() {
            UserService serviceWithoutCache = new UserService(userDao, transactionTemplate, passwordEncoder);

            serviceWithoutCache.cacheUser(new User("alice", "hashed"));

            verifyNoInteractions(userCache);
        }
    }
}
