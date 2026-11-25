package com.example.xsltservice.interceptor;

import com.example.xsltservice.entity.User;
import com.example.xsltservice.security.AuthUser;
import com.example.xsltservice.service.PricingPlanService;
import com.example.xsltservice.service.UserService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Optional;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String HEADER_RATE_LIMIT_REMAINING = "X-Rate-Limit-Remaining";
    private static final String HEADER_RETRY_AFTER_SECONDS = "X-Rate-Limit-Retry-After-Seconds";
    private static final String RATE_LIMIT_EXCEEDED_MESSAGE = "You have exhausted your API Request Quota";
    private static final String USER_NOT_FOUND_MESSAGE = "Authenticated user not found";
    private static final long NANOSECONDS_IN_SECOND = 1_000_000_000L;
    private static final long MIN_RETRY_AFTER_SECONDS = 1L;
    private static final long TOKENS_PER_REQUEST = 1L;

    private final PricingPlanService pricingPlanService;
    private final UserService userService;

    @Autowired
    public RateLimitInterceptor(PricingPlanService pricingPlanService, UserService userService) {
        this.pricingPlanService = pricingPlanService;
        this.userService = userService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        Optional<Authentication> authentication = resolveAuthenticatedSession();
        if (authentication.isEmpty()) {
            return true;
        }
        Optional<User> user = resolveUser(authentication.get());
        if (user.isEmpty()) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), USER_NOT_FOUND_MESSAGE);
            return false;
        }
        return consumeToken(user.get(), response);
    }

    private Optional<Authentication> resolveAuthenticatedSession() {
        SecurityContext context = SecurityContextHolder.getContext();
        Authentication authentication = context.getAuthentication();
        if (!isAuthenticatedUser(authentication)) {
            return Optional.empty();
        }
        return Optional.of(authentication);
    }

    private boolean isAuthenticatedUser(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && authentication.getPrincipal() instanceof UserDetails;
    }

    private Optional<User> resolveUser(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthUser authUser && authUser.getUser() != null) {
            return Optional.of(authUser.getUser());
        }
        UserDetails userDetails = (UserDetails) principal;
        return userService.findByUsername(userDetails.getUsername());
    }

    private boolean consumeToken(User user, HttpServletResponse response) throws IOException {
        Bucket tokenBucket = pricingPlanService.resolveBucket(user);
        ConsumptionProbe probe = tokenBucket.tryConsumeAndReturnRemaining(TOKENS_PER_REQUEST);
        if (probe.isConsumed()) {
            response.addHeader(HEADER_RATE_LIMIT_REMAINING, String.valueOf(probe.getRemainingTokens()));
            return true;
        }
        long waitSeconds = calculateRetryAfterSeconds(probe);
        response.addHeader(HEADER_RETRY_AFTER_SECONDS, String.valueOf(waitSeconds));
        response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), RATE_LIMIT_EXCEEDED_MESSAGE);
        return false;
    }

    private long calculateRetryAfterSeconds(ConsumptionProbe probe) {
        long nanosToWait = probe.getNanosToWaitForRefill();
        long roundedUpSeconds = (nanosToWait + NANOSECONDS_IN_SECOND - 1L) / NANOSECONDS_IN_SECOND;
        return Math.max(MIN_RETRY_AFTER_SECONDS, roundedUpSeconds);
    }
}
