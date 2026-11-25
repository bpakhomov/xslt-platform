package com.example.xsltservice.interceptor;

import com.example.xsltservice.entity.User;
import com.example.xsltservice.security.AuthUser;
import com.example.xsltservice.service.PricingPlanService;
import com.example.xsltservice.service.UserService;
import io.github.bucket4j.Bucket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.method.HandlerMethod;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RateLimitInterceptorTest {

    private PricingPlanService pricingPlanService;
    private UserService userService;
    private RateLimitInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private HandlerMethod handlerMethod;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        pricingPlanService = mock(PricingPlanService.class);
        userService = mock(UserService.class);
        interceptor = new RateLimitInterceptor(pricingPlanService, userService);
        request = new MockHttpServletRequest("POST", "/request");
        response = new MockHttpServletResponse();
        handlerMethod = new HandlerMethod(new SampleController(), SampleController.class.getDeclaredMethod("handle"));
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class PreHandle {

        @Test
        void allowsRequestAndSetsRemainingHeaderWhenTokenAvailable() throws Exception {
            User user = authenticateAuthUser("alice");
            Bucket bucket = createBucketWithCapacity(12);
            when(pricingPlanService.resolveBucket(user)).thenReturn(bucket);

            boolean allowed = interceptor.preHandle(request, response, handlerMethod);

            assertTrue(allowed);
            assertEquals("11", response.getHeader("X-Rate-Limit-Remaining"));
            verifyNoInteractions(userService);
        }

        @Test
        void rejectsWithTooManyRequestsWhenQuotaExhausted() throws Exception {
            User user = authenticateAuthUser("alice");
            Bucket bucket = createBucketWithCapacity(1);
            bucket.tryConsume(1);
            when(pricingPlanService.resolveBucket(user)).thenReturn(bucket);

            boolean allowed = interceptor.preHandle(request, response, handlerMethod);

            assertFalse(allowed);
            assertEquals(429, response.getStatus());
            assertTrue(Long.parseLong(response.getHeader("X-Rate-Limit-Retry-After-Seconds")) >= 1L);
        }

        @Test
        void loadsUserWhenPrincipalIsGenericUserDetails() throws Exception {
            UserDetails springUser = org.springframework.security.core.userdetails.User
                    .withUsername("bob")
                    .password("hash")
                    .authorities(List.of())
                    .build();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(springUser, "", springUser.getAuthorities())
            );
            User dbUser = new User("bob", "hash");
            when(userService.findByUsername("bob")).thenReturn(Optional.of(dbUser));
            when(pricingPlanService.resolveBucket(dbUser)).thenReturn(createBucketWithCapacity(12));

            boolean allowed = interceptor.preHandle(request, response, handlerMethod);

            assertTrue(allowed);
            verify(userService).findByUsername("bob");
        }

        @Test
        void rejectsWithUnauthorizedWhenGenericUserNotFound() throws Exception {
            UserDetails springUser = org.springframework.security.core.userdetails.User
                    .withUsername("missing")
                    .password("hash")
                    .authorities(List.of())
                    .build();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(springUser, "", springUser.getAuthorities())
            );
            when(userService.findByUsername("missing")).thenReturn(Optional.empty());

            boolean allowed = interceptor.preHandle(request, response, handlerMethod);

            assertFalse(allowed);
            assertEquals(401, response.getStatus());
            verifyNoInteractions(pricingPlanService);
        }

        @Test
        void allowsRequestWithoutRateLimitWhenUnauthenticated() throws Exception {
            boolean allowed = interceptor.preHandle(request, response, handlerMethod);

            assertTrue(allowed);
            verifyNoInteractions(pricingPlanService, userService);
        }

        @Test
        void allowsRequestWithoutRateLimitWhenHandlerIsNotControllerMethod() throws Exception {
            authenticateAuthUser("alice");

            boolean allowed = interceptor.preHandle(request, response, new Object());

            assertTrue(allowed);
            verifyNoInteractions(pricingPlanService, userService);
        }
    }

    private User authenticateAuthUser(String username) {
        User user = new User(username, "hash");
        AuthUser authUser = new AuthUser(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(authUser, "", authUser.getAuthorities())
        );
        return user;
    }

    private Bucket createBucketWithCapacity(long capacity) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillIntervally(capacity, Duration.ofMinutes(1)))
                .build();
    }

    private static class SampleController {
        void handle() {
        }
    }
}
