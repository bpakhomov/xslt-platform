package com.example.xsltconverter.filter;

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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class ApiLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiLoggingFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int VISIBLE_TOKEN_TAIL = 8;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
        throws ServletException, IOException {
        var wrappedReq = new ContentCachingRequestWrapper(req, 1024 * 1024);
        var wrappedRes = new ContentCachingResponseWrapper(res);
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(wrappedReq, wrappedRes);
        } finally {
            String reqBody = wrappedReq.getContentAsString();
            String resBody = new String(wrappedRes.getContentAsByteArray(), StandardCharsets.UTF_8);
            HttpHeaders filteredHeaders = filterHeaders(req);
            try {
                log.info("API {} {} -> {} ({} ms) | headers={} | req={} | res={}",
                        req.getMethod(), req.getRequestURI(), wrappedRes.getStatus(),
                        System.currentTimeMillis() - start,
                        filteredHeaders, reqBody, resBody);
            } finally {
                wrappedRes.copyBodyToResponse();
            }
        }
    }

    private HttpHeaders filterHeaders(HttpServletRequest req) {
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
        String token = header.substring(BEARER_PREFIX.length());
        if (token.length() <= VISIBLE_TOKEN_TAIL) {
            return BEARER_PREFIX + "***";
        }
        return BEARER_PREFIX + "..." + token.substring(token.length() -
                VISIBLE_TOKEN_TAIL);
    }
}
