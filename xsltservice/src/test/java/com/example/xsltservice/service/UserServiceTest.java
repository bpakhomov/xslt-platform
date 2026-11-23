package com.example.xsltservice.service;

import com.example.xsltservice.dao.UserDao;
import com.example.xsltservice.entity.User;
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
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userDao = mock(UserDao.class);
        passwordEncoder = new BCryptPasswordEncoder();
        userService = new UserService(
                userDao,
                new TransactionTemplate(mock(PlatformTransactionManager.class)),
                passwordEncoder
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
            verifyNoInteractions(userDao);
        }

        @Test
        void returnsUserWhenFound() {
            User user = new User("ivan", "qwerty");
            when(userDao.findByUsername("ivan")).thenReturn(Optional.of(user));

            Optional<User> found = userService.findByUsername("ivan");

            assertTrue(found.isPresent());
            assertEquals("ivan", found.get().getUsername());
        }

        @Test
        void returnsEmptyWhenNotFound() {
            when(userDao.findByUsername("missing")).thenReturn(Optional.empty());

            assertTrue(userService.findByUsername("missing").isEmpty());
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
        }

        @Test
        void updateNotExistingUser() {
            when(userDao.findById(1L)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> userService.updateUsername(1L, "name"));
            verify(userDao,never()).existsByUsername(any());
            verify(userDao, never()).update(any());
        }

        @Test
        void updateExistingUsername() {
            when(userDao.findById(1L)).thenReturn(Optional.of(new User()));
            when(userDao.existsByUsername(any())).thenReturn(true);

            assertThrows(IllegalArgumentException.class, () -> userService.updateUsername(1L, "name"));
            verify(userDao, never()).update(any());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void updateUsername(String username) {
            when(userDao.findById(1L)).thenReturn(Optional.of(new User()));

            assertThrows(IllegalArgumentException.class, () -> userService.updateUsername(1L, username));
            verify(userDao, never()).existsByUsername(any());
            verify(userDao, never()).update(any());
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
        }

        @Test
        void throwsWhenUserNotFound() {
            when(userDao.findById(1L)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> userService.updatePassword(1L, "newSecret"));
            verify(userDao, never()).update(any());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void throwsWhenPasswordIsBlank(String password) {
            assertThrows(IllegalArgumentException.class, () -> userService.updatePassword(1L, password));
            verifyNoInteractions(userDao);
        }
    }

    @Nested
    class DeleteById {
        @Test
        void deletesUserWhenExists() {
            when(userDao.deleteById(1L)).thenReturn(true);

            userService.deleteById(1L);

            verify(userDao).deleteById(1L);
        }

        @Test
        void throwsWhenUserNotFound() {
            when(userDao.deleteById(1L)).thenReturn(false);

            assertThrows(NoSuchElementException.class, () -> userService.deleteById(1L));
            verify(userDao).deleteById(1L);
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
}
