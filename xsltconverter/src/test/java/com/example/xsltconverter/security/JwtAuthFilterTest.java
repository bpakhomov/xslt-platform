package com.example.xsltconverter.security;

import com.example.xsltconverter.entity.User;
import com.example.xsltconverter.entity.UserStatus;
import com.example.xsltconverter.service.IssuedTokenService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthFilterTest {

    private JwtService jwtService;
    private UserDetailsService userDetailsService;
    private JwtAuthFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;
    private IssuedTokenService issuedTokenService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService("test-secret-key-at-least-32-bytes-long", 60);
        userDetailsService = mock(UserDetailsService.class);
        issuedTokenService = mock(IssuedTokenService.class);
        filter = new JwtAuthFilter(jwtService, userDetailsService, issuedTokenService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class DoFilterInternal {

        @Test
        void authenticatesValidBearerToken() throws Exception {
            String token = jwtService.generateToken("alice");
            when(issuedTokenService.isTokenActive(token)).thenReturn(true);
            when(userDetailsService.loadUserByUsername("alice"))
                    .thenReturn(new AuthUser(new User("alice", "hashed")));
            request.addHeader("Authorization", "Bearer " + token);

            filter.doFilter(request, response, chain);

            assertNotNull(SecurityContextHolder.getContext().getAuthentication());
            assertEquals("alice", SecurityContextHolder.getContext().getAuthentication().getName());
            verify(chain).doFilter(request, response);
        }

        @Test
        void leavesContextEmptyWhenUserIsDisabled() throws Exception {
            String token = jwtService.generateToken("alice");
            User disabledUser = new User("alice", "hashed");
            disabledUser.setStatus(UserStatus.DISABLED);
            when(issuedTokenService.isTokenActive(token)).thenReturn(true);
            when(userDetailsService.loadUserByUsername("alice"))
                    .thenReturn(new AuthUser(disabledUser));
            request.addHeader("Authorization", "Bearer " + token);

            filter.doFilter(request, response, chain);

            assertNull(SecurityContextHolder.getContext().getAuthentication());
            verify(chain).doFilter(request, response);
        }

        @Test
        void leavesContextEmptyWhenUserNotFound() throws Exception {
            String token = jwtService.generateToken("ghost");
            when(issuedTokenService.isTokenActive(token)).thenReturn(true);
            when(userDetailsService.loadUserByUsername("ghost"))
                    .thenThrow(new UsernameNotFoundException("Missing"));
            request.addHeader("Authorization", "Bearer " + token);

            filter.doFilter(request, response, chain);

            assertNull(SecurityContextHolder.getContext().getAuthentication());
            verify(chain).doFilter(request, response);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"Basic abc", "Bearer invalid-token"})
        void leavesContextEmptyWhenHeaderMissingOrInvalid(String header) throws Exception {
            if (header != null) {
                request.addHeader("Authorization", header);
            }

            filter.doFilter(request, response, chain);

            assertNull(SecurityContextHolder.getContext().getAuthentication());
            verifyNoInteractions(userDetailsService, issuedTokenService);
            verify(chain).doFilter(request, response);
        }
    }
}
