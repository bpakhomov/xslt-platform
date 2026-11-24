package com.example.xsltservice.service;

import com.example.xsltservice.dao.UserDao;
import com.example.xsltservice.entity.User;
import com.example.xsltservice.entity.UserStatus;
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

import java.util.List;
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
        void hashesPasswordAndSavesUser() {
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
    class GetById {
        @Test
        void returnsUserWhenFound() {
            User user = new User("alice", "hashed");
            when(userDao.findById(1L)).thenReturn(Optional.of(user));

            User found = userService.getById(1L);

            assertEquals("alice", found.getUsername());
        }

        @Test
        void throwsWhenUserNotFound() {
            when(userDao.findById(123L)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> userService.getById(123L));
        }
    }

    @Nested
    class FindByUsername {
        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void returnsEmptyWhenUsernameIsBlank(String username) {
            assertTrue(userService.findByUsername(username).isEmpty());
            verifyNoInteractions(userDao, userCache);
        }

        @Test
        void returnsCachedUserWithoutDatabaseLookup() {
            User cached = new User("ivan", "qwerty");
            cached.setId(5L);
            when(userCache.get("ivan")).thenReturn(Optional.of(cached));

            Optional<User> found = userService.findByUsername("ivan");

            assertTrue(found.isPresent());
            assertEquals(5L, found.get().getId());
            assertEquals("ivan", found.get().getUsername());
            verifyNoInteractions(userDao);
            verify(userCache, never()).put(any());
        }

        @Test
        void returnsUserWhenFound() {
            User user = new User("ivan", "qwerty");
            when(userCache.get("ivan")).thenReturn(Optional.empty());
            when(userDao.findByUsername("ivan")).thenReturn(Optional.of(user));

            Optional<User> found = userService.findByUsername("ivan");

            assertTrue(found.isPresent());
            assertEquals("ivan", found.get().getUsername());
            verify(userCache).put(user);
        }

        @Test
        void loadsFromDatabaseWhenCacheIsNull() {
            UserService serviceWithoutCache = new UserService(userDao, transactionTemplate, passwordEncoder);
            User user = new User("ivan", "qwerty");
            when(userDao.findByUsername("ivan")).thenReturn(Optional.of(user));

            Optional<User> found = serviceWithoutCache.findByUsername("ivan");

            assertTrue(found.isPresent());
            assertEquals("ivan", found.get().getUsername());
            verify(userDao).findByUsername("ivan");
        }

        @Test
        void returnsEmptyWhenNotFound() {
            when(userCache.get("missing")).thenReturn(Optional.empty());
            when(userDao.findByUsername("missing")).thenReturn(Optional.empty());

            assertTrue(userService.findByUsername("missing").isEmpty());
            verify(userCache, never()).put(any());
        }
    }

    @Nested
    class FindAll {
        @Test
        void returnsNullWhenNotFound() {
            when(userDao.findAll()).thenReturn(null);

            assertEquals(List.of(), userService.findAll());
        }

        @Test
        void returnsWhenFound() {
            List<User> users = List.of(
                new User(),
                new User(),
                new User()
            );
            when(userDao.findAll()).thenReturn(users);

            List<User> foundUsers = userService.findAll();

            assertFalse(foundUsers.isEmpty());
            assertEquals(3, foundUsers.size());
        }
    }

    @Nested
    class UpdateUsername {
        @Test
        void updateUsernameSuccessful() {
            User user = new User("name", "1234");
            when(userDao.findById(1L)).thenReturn(Optional.of(user));
            when(userDao.existsByUsername("newName")).thenReturn(false);
            when(userDao.update(user)).thenReturn(user);

            User updated = userService.updateUsername(1L, "newName");

            assertEquals("newName", updated.getUsername());
            verify(userDao).existsByUsername("newName");
            verify(userDao).update(user);
            verify(userCache).evict("name");
            verify(userCache).put(updated);
        }

        @Test
        void updateNotExistingUser() {
            when(userDao.findById(1L)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> userService.updateUsername(1L, "name"));
            verify(userDao, never()).existsByUsername(any());
            verify(userDao, never()).update(any());
            verifyNoInteractions(userCache);
        }

        @Test
        void updateExistingUsername() {
            when(userDao.findById(1L)).thenReturn(Optional.of(new User()));
            when(userDao.existsByUsername(any())).thenReturn(true);

            assertThrows(IllegalArgumentException.class, () -> userService.updateUsername(1L, "name"));
            verify(userDao, never()).update(any());
            verifyNoInteractions(userCache);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void updateUsername(String username) {
            assertThrows(IllegalArgumentException.class, () -> userService.updateUsername(1L, username));
            verifyNoInteractions(userDao, userCache);
        }

        @Test
        void updateUsernameOnCurrent() {
            User user = new User("name", "pass");
            when(userDao.findById(1L)).thenReturn(Optional.of(user));
            when(userDao.update(user)).thenReturn(user);

            User updated = userService.updateUsername(1L, "name");

            assertEquals("name", updated.getUsername());
            verify(userDao, never()).existsByUsername(any());
            verify(userDao).update(user);
            verify(userCache, never()).evict(any());
            verify(userCache).put(updated);
        }

        @Test
        void updatesUsernameWhenCacheIsNull() {
            UserService serviceWithoutCache = new UserService(userDao, transactionTemplate, passwordEncoder);
            User user = new User("name", "1234");
            when(userDao.findById(1L)).thenReturn(Optional.of(user));
            when(userDao.existsByUsername("newName")).thenReturn(false);
            when(userDao.update(user)).thenReturn(user);

            User updated = serviceWithoutCache.updateUsername(1L, "newName");

            assertEquals("newName", updated.getUsername());
            verify(userDao).update(user);
        }
    }

    @Nested
    class UpdatePassword {
        @Test
        void updatePasswordSuccessful() {
            User user = new User("alice", "oldHash");
            when(userDao.findById(1L)).thenReturn(Optional.of(user));
            when(userDao.update(user)).thenReturn(user);

            User updated = userService.updatePassword(1L, "newSecret");

            assertNotEquals("newSecret", updated.getPassword());
            assertNotEquals("oldHash", updated.getPassword());
            assertTrue(passwordEncoder.matches("newSecret", updated.getPassword()));
            verify(userDao).update(user);
            verify(userCache).put(updated);
        }

        @Test
        void throwsWhenUserNotFound() {
            when(userDao.findById(1L)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> userService.updatePassword(1L, "newSecret"));
            verify(userDao, never()).update(any());
            verifyNoInteractions(userCache);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void throwsWhenPasswordIsBlank(String password) {
            assertThrows(IllegalArgumentException.class, () -> userService.updatePassword(1L, password));
            verifyNoInteractions(userDao, userCache);
        }
    }

    @Nested
    class DeactivateById {
        @Test
        void deactivatesUserAndOverwritesCache() {
            User user = new User("alice", "hash");
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
            User user = new User("alice", "hash");
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
    class DeleteById {
        @Test
        void deletesUserWhenExists() {
            User user = new User("alice", "hash");
            when(userDao.findById(1L)).thenReturn(Optional.of(user));
            when(userDao.deleteById(1L)).thenReturn(true);

            userService.deleteById(1L);

            verify(userDao).deleteById(1L);
            verify(userCache).evict("alice");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void deletesUserWithoutEvictingWhenUsernameIsBlank(String username) {
            User user = new User(username, "hash");
            when(userDao.findById(1L)).thenReturn(Optional.of(user));
            when(userDao.deleteById(1L)).thenReturn(true);

            userService.deleteById(1L);

            verify(userDao).deleteById(1L);
            verifyNoInteractions(userCache);
        }

        @Test
        void deletesUserWhenCacheIsNull() {
            UserService serviceWithoutCache = new UserService(userDao, transactionTemplate, passwordEncoder);
            User user = new User("alice", "hash");
            when(userDao.findById(1L)).thenReturn(Optional.of(user));
            when(userDao.deleteById(1L)).thenReturn(true);

            serviceWithoutCache.deleteById(1L);

            verify(userDao).deleteById(1L);
        }

        @Test
        void throwsWhenUserNotFound() {
            when(userDao.findById(1L)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> userService.deleteById(1L));
            verify(userDao, never()).deleteById(anyLong());
            verifyNoInteractions(userCache);
        }

        @Test
        void throwsWhenDeleteReturnsFalse() {
            User user = new User("alice", "hash");
            when(userDao.findById(1L)).thenReturn(Optional.of(user));
            when(userDao.deleteById(1L)).thenReturn(false);

            assertThrows(NoSuchElementException.class, () -> userService.deleteById(1L));
            verifyNoInteractions(userCache);
        }
    }

    @Nested
    class MatchesPassword {
        @Test
        void returnsTrueWhenPasswordMatches() {
            String encoded = passwordEncoder.encode("secret123");

            assertTrue(userService.matchesPassword("secret123", encoded));
            verifyNoInteractions(userDao);
        }

        @Test
        void returnsFalseWhenPasswordDoesNotMatch() {
            String encoded = passwordEncoder.encode("secret123");

            assertFalse(userService.matchesPassword("wrongPassword", encoded));
            verifyNoInteractions(userDao);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void returnsFalseWhenRawPasswordIsBlank(String rawPassword) {
            assertFalse(userService.matchesPassword(rawPassword, "encodedHash"));
            verifyNoInteractions(userDao);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void returnsFalseWhenEncodedPasswordIsBlank(String encodedPassword) {
            assertFalse(userService.matchesPassword("secret123", encodedPassword));
            verifyNoInteractions(userDao);
        }
    }

    @Nested
    class ReadFromCache {
        @Test
        void returnsCachedUserWhenPresent() {
            User cached = new User("alice", "hash");
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
            User user = new User("alice", "hash");

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

            serviceWithoutCache.cacheUser(new User("alice", "hash"));

            verifyNoInteractions(userCache);
        }
    }

    @Nested
    class EvictFromCache {
        @Test
        void evictsUserWhenCacheAndUsernameArePresent() {
            userService.evictFromCache("alice");

            verify(userCache).evict("alice");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void doesNothingWhenUsernameIsBlank(String username) {
            userService.evictFromCache(username);

            verifyNoInteractions(userCache);
        }

        @Test
        void doesNothingWhenCacheIsNull() {
            UserService serviceWithoutCache = new UserService(userDao, transactionTemplate, passwordEncoder);

            serviceWithoutCache.evictFromCache("alice");

            verifyNoInteractions(userCache);
        }
    }
}
