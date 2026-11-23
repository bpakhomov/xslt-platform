package com.example.xsltconverter.security;

import com.example.xsltconverter.entity.User;
import com.example.xsltconverter.entity.UserStatus;
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
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    private JwtService jwtService;
    private UserDetailsService userDetailsService;
    private JwtAuthFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new ObjectMapper(), "test-secret-key-at-least-32-bytes-long", 60);
        userDetailsService = mock(UserDetailsService.class);
        filter = new JwtAuthFilter(jwtService, userDetailsService);
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
            verifyNoInteractions(userDetailsService);
            verify(chain).doFilter(request, response);
        }
    }
}
