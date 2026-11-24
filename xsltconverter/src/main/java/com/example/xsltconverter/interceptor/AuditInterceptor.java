package com.example.xsltconverter.interceptor;

import com.example.xsltconverter.dto.AuditRecord;
import com.example.xsltconverter.entity.User;
import com.example.xsltconverter.security.AuthUser;
import com.example.xsltconverter.service.AuditService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
public class AuditInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTRIBUTE = AuditInterceptor.class.getName() + ".START_TIME_NANOS";
    private static final double NANOSECONDS_IN_MILLISECOND = 1_000_000.0;
    private static final String METHOD_SEPARATOR = "#";
    private static final String PARAMETER_SEPARATOR = ", ";
    private static final String KEY_VALUE_SEPARATOR = "=";
    private static final String MULTI_VALUE_SEPARATOR = ",";
    private static final String EXCEPTION_MESSAGE_SEPARATOR = ": ";
    private static final String CHARSET_PREFIX = "charset=";

    private final AuditService auditService;

    @Autowired
    public AuditInterceptor(AuditService auditService) {
        this.auditService = auditService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod) {
            request.setAttribute(START_TIME_ATTRIBUTE, System.nanoTime());
        }
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        if (!isAuditableRequest(request, handler)) {
            return;
        }
        Optional<AuthenticatedAccount> account = resolveAuthenticatedAccount();
        if (account.isEmpty()) {
            return;
        }
        AuditTarget target = createAuditTarget(account.get(), (HandlerMethod) handler, request);
        RequestSnapshot requestSnapshot = captureRequest(request);
        ResponseSnapshot responseSnapshot = captureResponse(response, exception);
        auditService.record(toAuditRecord(target, requestSnapshot, responseSnapshot));
    }

    private boolean isAuditableRequest(HttpServletRequest request, Object handler) {
        return request.getDispatcherType() == DispatcherType.REQUEST && handler instanceof HandlerMethod;
    }

    private Optional<AuthenticatedAccount> resolveAuthenticatedAccount() {
        SecurityContext context = SecurityContextHolder.getContext();
        Authentication authentication = context.getAuthentication();
        if (!isAuthenticatedUser(authentication)) {
            return Optional.empty();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthUser authUser) {
            return accountFromAuthUser(authUser);
        }
        return accountFromAuthenticationName(authentication);
    }

    private boolean isAuthenticatedUser(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private Optional<AuthenticatedAccount> accountFromAuthUser(AuthUser authUser) {
        User user = authUser.getUser();
        if (user == null || isBlank(user.getUsername())) {
            return Optional.empty();
        }
        return Optional.of(new AuthenticatedAccount(user.getUsername(), user.getId()));
    }

    private Optional<AuthenticatedAccount> accountFromAuthenticationName(Authentication authentication) {
        String username = authentication.getName();
        if (isBlank(username)) {
            return Optional.empty();
        }
        return Optional.of(new AuthenticatedAccount(username, AuditRecord.UNKNOWN_USER_ID));
    }

    private AuditTarget createAuditTarget(
            AuthenticatedAccount account,
            HandlerMethod handlerMethod,
            HttpServletRequest request
    ) {
        String controllerMethod = extractControllerMethod(handlerMethod);
        double durationMilliseconds = calculateDurationMilliseconds(request);
        return new AuditTarget(account, controllerMethod, durationMilliseconds);
    }

    private String extractControllerMethod(HandlerMethod handlerMethod) {
        Class<?> controllerType = handlerMethod.getBeanType();
        Method method = handlerMethod.getMethod();
        return controllerType.getSimpleName() + METHOD_SEPARATOR + method.getName();
    }

    private double calculateDurationMilliseconds(HttpServletRequest request) {
        Object attribute = request.getAttribute(START_TIME_ATTRIBUTE);
        if (!(attribute instanceof Long startTimeNanos)) {
            return 0.0;
        }
        long elapsedNanos = Math.max(0L, System.nanoTime() - startTimeNanos);
        return elapsedNanos / NANOSECONDS_IN_MILLISECOND;
    }

    private RequestSnapshot captureRequest(HttpServletRequest request) {
        String httpMethod = defaultText(request.getMethod());
        String requestPath = defaultText(request.getRequestURI());
        String requestParameters = extractRequestParameters(request);
        String requestBody = extractRequestBody(request);
        return new RequestSnapshot(httpMethod, requestPath, requestParameters, requestBody);
    }

    private String extractRequestParameters(HttpServletRequest request) {
        String pathVariables = formatPathVariables(request);
        String queryParameters = formatQueryParameters(request);
        return combineParameters(pathVariables, queryParameters);
    }

    private String formatPathVariables(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attribute instanceof Map<?, ?> rawVariables) || rawVariables.isEmpty()) {
            return "";
        }
        Map<String, String> sortedVariables = new TreeMap<>();
        rawVariables.forEach((key, value) -> sortedVariables.put(String.valueOf(key), String.valueOf(value)));
        return joinFormattedEntries(sortedVariables);
    }

    private String formatQueryParameters(HttpServletRequest request) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        if (parameterMap == null || parameterMap.isEmpty()) {
            return "";
        }
        Map<String, String> sortedParameters = new TreeMap<>();
        parameterMap.forEach((key, values) -> sortedParameters.put(key, formatParameterValues(values)));
        return joinFormattedEntries(sortedParameters);
    }

    private String formatParameterValues(String[] values) {
        if (values == null || values.length == 0) {
            return "";
        }
        return String.join(MULTI_VALUE_SEPARATOR, values);
    }

    private String joinFormattedEntries(Map<String, String> entries) {
        if (entries.isEmpty()) {
            return "";
        }
        return entries.entrySet().stream()
                .map(entry -> entry.getKey() + KEY_VALUE_SEPARATOR + entry.getValue())
                .collect(Collectors.joining(PARAMETER_SEPARATOR));
    }

    private String combineParameters(String pathVariables, String queryParameters) {
        if (pathVariables.isEmpty()) {
            return queryParameters;
        }
        if (queryParameters.isEmpty()) {
            return pathVariables;
        }
        return pathVariables + PARAMETER_SEPARATOR + queryParameters;
    }

    private String extractRequestBody(HttpServletRequest request) {
        ContentCachingRequestWrapper wrapper = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        if (wrapper == null) {
            return "";
        }
        return decodeContent(wrapper.getContentAsByteArray(), wrapper.getCharacterEncoding(), wrapper.getContentType());
    }

    private ResponseSnapshot captureResponse(HttpServletResponse response, Exception exception) {
        int status = response.getStatus();
        String responseBody = extractResponseBody(response);
        String errorMessage = extractErrorMessage(exception);
        return new ResponseSnapshot(status, responseBody, errorMessage);
    }

    private String extractResponseBody(HttpServletResponse response) {
        ContentCachingResponseWrapper wrapper = WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);
        if (wrapper == null) {
            return "";
        }
        return decodeContent(wrapper.getContentAsByteArray(), wrapper.getCharacterEncoding(), wrapper.getContentType());
    }

    private String decodeContent(byte[] content, String characterEncoding, String contentType) {
        if (content.length == 0) {
            return "";
        }
        return new String(content, resolveCharset(characterEncoding, contentType));
    }

    private Charset resolveCharset(String characterEncoding, String contentType) {
        if (isBlank(characterEncoding)) {
            return StandardCharsets.UTF_8;
        }
        if (StandardCharsets.ISO_8859_1.name().equalsIgnoreCase(characterEncoding) && !hasExplicitCharset(contentType)) {
            return StandardCharsets.UTF_8;
        }
        return parseCharsetOrDefault(characterEncoding);
    }

    private boolean hasExplicitCharset(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).contains(CHARSET_PREFIX);
    }

    private Charset parseCharsetOrDefault(String encodingName) {
        try {
            return Charset.forName(encodingName);
        } catch (IllegalArgumentException exception) {
            return StandardCharsets.UTF_8;
        }
    }

    private String extractErrorMessage(Exception exception) {
        if (exception == null) {
            return "";
        }
        String className = exception.getClass().getSimpleName();
        String detail = exception.getMessage();
        if (isBlank(detail)) {
            return className;
        }
        return className + EXCEPTION_MESSAGE_SEPARATOR + detail;
    }

    private AuditRecord toAuditRecord(
            AuditTarget target,
            RequestSnapshot requestSnapshot,
            ResponseSnapshot responseSnapshot
    ) {
        AuthenticatedAccount account = target.account();
        return new AuditRecord(
                account.username(),
                account.userId(),
                requestSnapshot.httpMethod(),
                requestSnapshot.requestPath(),
                target.controllerMethod(),
                requestSnapshot.requestParameters(),
                requestSnapshot.requestBody(),
                responseSnapshot.status(),
                responseSnapshot.body(),
                responseSnapshot.errorMessage(),
                target.durationMilliseconds()
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String defaultText(String value) {
        return value != null ? value : "";
    }

    private record AuthenticatedAccount(String username, long userId) {
    }

    private record AuditTarget(AuthenticatedAccount account, String controllerMethod, double durationMilliseconds) {
    }

    private record RequestSnapshot(
            String httpMethod,
            String requestPath,
            String requestParameters,
            String requestBody
    ) {
    }

    private record ResponseSnapshot(int status, String body, String errorMessage) {
    }
}
