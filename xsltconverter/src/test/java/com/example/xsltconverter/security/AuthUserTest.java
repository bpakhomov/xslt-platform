package com.example.xsltconverter.security;

import com.example.xsltconverter.entity.User;
import com.example.xsltconverter.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthUserTest {

    private User user;
    private AuthUser authUser;

    @BeforeEach
    void setUp() {
        user = new User("alice", "hashed");
        authUser = new AuthUser(user);
    }

    @Nested
    class IsEnabled {

        @Test
        void returnsTrueWhenUserIsActive() {
            user.setStatus(UserStatus.ACTIVE);

            assertTrue(authUser.isEnabled());
        }

        @Test
        void returnsFalseWhenUserIsDisabled() {
            user.setStatus(UserStatus.DISABLED);

            assertFalse(authUser.isEnabled());
        }

        @Test
        void returnsFalseWhenStatusIsNull() {
            user.setStatus(null);

            assertFalse(authUser.isEnabled());
        }
    }
}
