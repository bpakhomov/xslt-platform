package com.example.xsltconverter.controller;

import com.example.xsltconverter.entity.User;
import com.example.xsltconverter.security.AuthUser;
import com.example.xsltconverter.service.IssuedTokenService;
import com.example.xsltconverter.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    private UserService userService;
    private AuthenticationManager authenticationManager;
    private MockMvc mockMvc;
    private IssuedTokenService issuedTokenService;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        authenticationManager = mock(AuthenticationManager.class);
        issuedTokenService = mock(IssuedTokenService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(userService, authenticationManager, issuedTokenService))
                .build();
    }

    @Nested
    class Register {

        @Test
        void registersUserAndReturnsCreatedWithoutPassword() throws Exception {
            User saved = new User("alice", "$2a$10$hash");
            saved.setId(1L);
            when(userService.createUser("alice", "secret")).thenReturn(saved);

            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"alice\",\"password\":\"secret\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.username").value("alice"))
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        void returnsBadRequestWhenUsernameAlreadyExists() throws Exception {
            when(userService.createUser("alice", "secret"))
                    .thenThrow(new IllegalArgumentException("Username already exists: alice"));

            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"alice\",\"password\":\"secret\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail").value("Username already exists: alice"));
        }

        @Test
        void returnsBadRequestWhenCredentialsAreBlank() throws Exception {
            mockMvc.perform(post("/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\" \",\"password\":\"\"}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(userService);
        }
    }

    @Nested
    class Login {

        @Test
        void logsInAndReturnsJwtToken() throws Exception {
            User user = new User("alice", "$2a$10$hash");
            when(authenticationManager.authenticate(any()))
                    .thenReturn(new UsernamePasswordAuthenticationToken(new AuthUser(user), null));
            when(issuedTokenService.issueToken(user)).thenReturn("jwt-token");

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"alice\",\"password\":\"secret\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("jwt-token"));
        }

        @Test
        void returnsUnauthorizedOnBadCredentials() throws Exception {
            when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("Invalid username or password"));
        }

        @Test
        void returnsBadRequestWhenCredentialsAreBlank() throws Exception {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"\",\"password\":\"\"}"))
                    .andExpect(status().isBadRequest());

            verifyNoInteractions(authenticationManager);
        }
    }
}
