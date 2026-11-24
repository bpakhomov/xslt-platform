package com.example.xsltservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

import java.io.IOException;

@Component
public class AuditContentCachingFilter extends OncePerRequestFilter {

    private static final int MAX_REQUEST_CACHE_BYTES = 1024 * 1024;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        HttpServletRequest requestToUse = wrapRequestIfNeeded(request);
        HttpServletResponse responseToUse = wrapResponseIfNeeded(response);
        try {
            filterChain.doFilter(requestToUse, responseToUse);
        } finally {
            copyResponseIfWrappedLocally(responseToUse, response);
        }
    }

    private HttpServletRequest wrapRequestIfNeeded(HttpServletRequest request) {
        if (WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class) != null) {
            return request;
        }
        return new ContentCachingRequestWrapper(request, MAX_REQUEST_CACHE_BYTES);
    }

    private HttpServletResponse wrapResponseIfNeeded(HttpServletResponse response) {
        if (WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class) != null) {
            return response;
        }
        return new ContentCachingResponseWrapper(response);
    }

    private void copyResponseIfWrappedLocally(
            HttpServletResponse candidateResponse,
            HttpServletResponse originalResponse
    ) throws IOException {
        if (candidateResponse != originalResponse
                && candidateResponse instanceof ContentCachingResponseWrapper wrapper) {
            wrapper.copyBodyToResponse();
        }
    }
}
