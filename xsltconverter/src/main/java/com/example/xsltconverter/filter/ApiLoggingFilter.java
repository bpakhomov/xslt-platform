package com.example.xsltconverter.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
            log.info("API {} {} -> {} ({} ms) | headers={} | req={} | res={}",
                req.getMethod(), req.getRequestURI(), wrappedRes.getStatus(),
                System.currentTimeMillis() - start,
                new ServletServerHttpRequest(req).getHeaders(), reqBody, resBody);
            wrappedRes.copyBodyToResponse();
        }
    }
}
