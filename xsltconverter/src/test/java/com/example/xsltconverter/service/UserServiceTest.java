package com.example.xsltconverter.service;

import com.example.xsltconverter.dao.UserDao;
import com.example.xsltconverter.entity.IssuedToken;
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
    private TransactionTemplate transactionTemplate;
    private PasswordEncoder passwordEncoder;
    private UserService userService;
    private IssuedTokenService issuedTokenService;

    @BeforeEach
    void setUp() {
        userDao = mock(UserDao.class);
        transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(userDao, transactionTemplate, passwordEncoder, issuedTokenService);
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
        }

        @Test
        void throwsWhenUsernameAlreadyExists() {
            when(userDao.existsByUsername("alice")).thenReturn(true);

            assertThrows(IllegalArgumentException.class, () -> userService.createUser("alice", "secret123"));
            verify(userDao, never()).save(any());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void throwsWhenUsernameIsBlank(String username) {
            assertThrows(IllegalArgumentException.class, () -> userService.createUser(username, "secret123"));
            verifyNoInteractions(userDao);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void throwsWhenPasswordIsBlank(String password) {
            assertThrows(IllegalArgumentException.class, () -> userService.createUser("alice", password));
            verifyNoInteractions(userDao);
        }
    }

    @Nested
    class EnsureUserExists {

        @Test
        void createsUserWhenMissing() {
            when(userDao.existsByUsername("xsltservice")).thenReturn(false);
            when(userDao.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

            userService.ensureUserExists("xsltservice", "secret123");

            verify(userDao).save(any(User.class));
        }

        @Test
        void skipsCreationWhenUserAlreadyExists() {
            when(userDao.existsByUsername("xsltservice")).thenReturn(true);

            userService.ensureUserExists("xsltservice", "secret123");

            verify(userDao, never()).save(any());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void ignoresBlankUsername(String username) {
            userService.ensureUserExists(username, "secret123");

            verifyNoInteractions(userDao);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void ignoresBlankPassword(String password) {
            userService.ensureUserExists("xsltservice", password);

            verifyNoInteractions(userDao);
        }
    }

    @Nested
    class FindByUsername {

        @Test
        void loadsFromDatabaseWhenFound() {
            User dbUser = new User("alice", "hashed");
            dbUser.setId(5L);
            when(userDao.findByUsername("alice")).thenReturn(Optional.of(dbUser));

            Optional<User> found = userService.findByUsername("alice");

            assertTrue(found.isPresent());
            assertEquals(5L, found.get().getId());
            assertEquals("alice", found.get().getUsername());
            verify(userDao).findByUsername("alice");
        }

        @Test
        void returnsEmptyWhenNotFound() {
            when(userDao.findByUsername("missing")).thenReturn(Optional.empty());

            assertTrue(userService.findByUsername("missing").isEmpty());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void returnsEmptyWhenUsernameIsBlank(String username) {
            assertTrue(userService.findByUsername(username).isEmpty());
            verifyNoInteractions(userDao);
        }
    }

    @Nested
    class DeactivateById {

        @Test
        void deactivatesUser() {
            User user = new User("alice", "hashed");
            user.setId(1L);
            when(userDao.findById(1L)).thenReturn(Optional.of(user));
            when(userDao.update(user)).thenReturn(user);

            User result = userService.deactivateById(1L);

            assertEquals(UserStatus.DISABLED, result.getStatus());
            assertFalse(result.isActive());
            verify(userDao).update(user);
        }

        @Test
        void throwsWhenUserNotFound() {
            when(userDao.findById(1L)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> userService.deactivateById(1L));
            verify(userDao, never()).update(any());
        }
    }

    @Nested
    class ActivateById {

        @Test
        void activatesUser() {
            User user = new User("alice", "hashed");
            user.setId(1L);
            user.setStatus(UserStatus.DISABLED);
            when(userDao.findById(1L)).thenReturn(Optional.of(user));
            when(userDao.update(user)).thenReturn(user);

            User result = userService.activateById(1L);

            assertEquals(UserStatus.ACTIVE, result.getStatus());
            assertTrue(result.isActive());
            verify(userDao).update(user);
        }

        @Test
        void throwsWhenUserNotFound() {
            when(userDao.findById(1L)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> userService.activateById(1L));
            verify(userDao, never()).update(any());
        }
    }
}
