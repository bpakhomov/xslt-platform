package com.example.xsltconverter.filter;

import com.example.xsltconverter.security.TokenMasker;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class ApiLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiLoggingFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOKEN_FIELD = "token";
    private static final int MAX_REQUEST_CACHE_BYTES = 1024 * 1024;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        var wrappedReq = new ContentCachingRequestWrapper(req, MAX_REQUEST_CACHE_BYTES);
        var wrappedRes = new ContentCachingResponseWrapper(res);
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(wrappedReq, wrappedRes);
        } finally {
            String reqBody = wrappedReq.getContentAsString();
            String resBody = maskResponse(new String(wrappedRes.getContentAsByteArray(), StandardCharsets.UTF_8));
            HttpHeaders maskedHeaders = maskHeaders(req);
            try {
                log.info("API {} {} -> {} ({} ms) | headers={} | req={} | res={}",
                        req.getMethod(), req.getRequestURI(), wrappedRes.getStatus(),
                        System.currentTimeMillis() - start,
                        maskedHeaders, reqBody, resBody);
            } finally {
                wrappedRes.copyBodyToResponse();
            }
        }
    }

    private HttpHeaders maskHeaders(HttpServletRequest req) {
        HttpHeaders headers = new ServletServerHttpRequest(req).getHeaders();
        String authorization = req.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null) {
            headers.set(HttpHeaders.AUTHORIZATION, maskAuthorization(authorization));
        }
        return headers;
    }

    private String maskAuthorization(String header) {
        if (!header.startsWith(BEARER_PREFIX)) {
            return "***";
        }
        return BEARER_PREFIX + TokenMasker.maskToken(header.substring(BEARER_PREFIX.length()));
    }

    private String maskResponse(String resBody) {
        if (resBody == null || !resBody.contains("\"" + TOKEN_FIELD + "\"")) {
            return resBody;
        }
        try {
            JsonNode root = objectMapper.readTree(resBody);
            if (root instanceof ObjectNode objectNode && objectNode.has(TOKEN_FIELD) && objectNode.get(TOKEN_FIELD).isString()) {
                objectNode.put(TOKEN_FIELD, TokenMasker.maskToken(objectNode.get(TOKEN_FIELD).asString()));
                return objectMapper.writeValueAsString(objectNode);
            }
        } catch (JacksonException ignored) {
        }
        return resBody;
    }
}
