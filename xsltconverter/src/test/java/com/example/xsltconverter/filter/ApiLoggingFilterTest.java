package com.example.xsltconverter.filter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApiLoggingFilterTest {

    private ApiLoggingFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain chain;
    private Logger filterLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        filter = new ApiLoggingFilter();
        request = new MockHttpServletRequest("POST", "/auth/login");
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
        filterLogger = (Logger) LoggerFactory.getLogger(ApiLoggingFilter.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        filterLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Nested
    class MaskHeaders {

        @Test
        void masksBearerAuthorizationHeader() throws Exception {
            String rawToken = "eyJhbGciOiJIUzI1NiJ9.payload.12345678";
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + rawToken);

            filter.doFilter(request, response, chain);

            String logMessage = logAppender.list.getLast().getFormattedMessage();
            assertTrue(logMessage.contains("Bearer ...12345678"));
            assertFalse(logMessage.contains(rawToken));
            verify(chain).doFilter(any(), any());
        }

        @Test
        void masksNonBearerAuthorizationHeader() throws Exception {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz");

            filter.doFilter(request, response, chain);

            String logMessage = logAppender.list.getLast().getFormattedMessage();
            assertTrue(logMessage.contains("Authorization:\"***\""));
            assertFalse(logMessage.contains("dXNlcjpwYXNz"));
        }
    }

    @Nested
    class MaskResponse {

        @Test
        void masksTokenFieldInResponseLogWhileSendingFullTokenToClient() throws Exception {
            String rawToken = "eyJhbGciOiJIUzI1NiJ9.payload.87654321";
            String responseJson = "{\"token\":\"" + rawToken + "\"}";
            stubChainResponse(MediaType.APPLICATION_JSON_VALUE, responseJson);

            filter.doFilter(request, response, chain);

            assertEquals(responseJson, response.getContentAsString(StandardCharsets.UTF_8));
            String logMessage = logAppender.list.getLast().getFormattedMessage();
            assertTrue(logMessage.contains("res={\"token\":\"...87654321\"}"));
            assertFalse(logMessage.contains(rawToken));
        }

        @Test
        void leavesResponseWithoutTokenFieldUnchanged() throws Exception {
            String responseJson = "{\"person\":{\"name\":\"Bob\"}}";
            stubChainResponse(MediaType.APPLICATION_JSON_VALUE, responseJson);

            filter.doFilter(request, response, chain);

            assertTrue(logAppender.list.getLast().getFormattedMessage().contains("res=" + responseJson));
        }
    }

    private void stubChainResponse(String contentType, String body) throws Exception {
        doAnswer(invocation -> {
            HttpServletResponse res = invocation.getArgument(1);
            res.setStatus(HttpServletResponse.SC_OK);
            res.setContentType(contentType);
            res.setCharacterEncoding(StandardCharsets.UTF_8.name());
            res.getWriter().write(body);
            return null;
        }).when(chain).doFilter(any(), any());
    }
}
