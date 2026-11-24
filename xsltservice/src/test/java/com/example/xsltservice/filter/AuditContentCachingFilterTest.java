package com.example.xsltservice.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class AuditContentCachingFilterTest {

    private AuditContentCachingFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new AuditContentCachingFilter();
        request = new MockHttpServletRequest("POST", "/request");
        response = new MockHttpServletResponse();
    }

    @Nested
    class DoFilterInternal {

        @Test
        void wrapsRequestAndResponseAndCopiesBody() throws Exception {
            request.setContent("{\"xml\":\"<a/>\"}".getBytes(StandardCharsets.UTF_8));
            FilterChain chain = (servletRequest, servletResponse) -> {
                assertInstanceOf(ContentCachingRequestWrapper.class, servletRequest);
                assertInstanceOf(ContentCachingResponseWrapper.class, servletResponse);
                servletRequest.getInputStream().readAllBytes();
                servletResponse.getOutputStream().write("{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
            };

            filter.doFilter(request, response, chain);

            assertEquals("{\"ok\":true}", response.getContentAsString(StandardCharsets.UTF_8));
        }

        @Test
        void preservesExistingCachingWrappers() throws Exception {
            ContentCachingRequestWrapper existingRequest = new ContentCachingRequestWrapper(request, 1024);
            ContentCachingResponseWrapper existingResponse = new ContentCachingResponseWrapper(response);
            FilterChain chain = (servletRequest, servletResponse) -> {
                assertSame(existingRequest, servletRequest);
                assertSame(existingResponse, servletResponse);
            };

            filter.doFilter(existingRequest, existingResponse, chain);
        }
    }
}
