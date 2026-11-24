package com.example.xsltconverter.interceptor;

import com.example.xsltconverter.dto.AuditRecord;
import com.example.xsltconverter.entity.User;
import com.example.xsltconverter.security.AuthUser;
import com.example.xsltconverter.service.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AuditInterceptorTest {

    private AuditService auditService;
    private AuditInterceptor interceptor;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private HandlerMethod handlerMethod;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        auditService = mock(AuditService.class);
        interceptor = new AuditInterceptor(auditService);
        request = new MockHttpServletRequest("POST", "/xml2format");
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
        void returnsTrueForHandlerMethod() {
            assertTrue(interceptor.preHandle(request, response, handlerMethod));
        }
    }

    @Nested
    class AfterCompletion {

        @Test
        void recordsAuditForAuthenticatedUser() throws Exception {
            authenticateAs("alice", 42L);
            request.addParameter("type", "XML_TO_XML");
            ContentCachingRequestWrapper wrappedRequest = wrapRequest("<hello/>");
            ContentCachingResponseWrapper wrappedResponse = wrapResponse("<hello/>");

            interceptor.preHandle(wrappedRequest, wrappedResponse, handlerMethod);
            interceptor.afterCompletion(wrappedRequest, wrappedResponse, handlerMethod, null);

            ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
            verify(auditService).record(captor.capture());
            AuditRecord recorded = captor.getValue();
            assertEquals("alice", recorded.username());
            assertEquals(42L, recorded.userId());
            assertEquals("SampleController#handle", recorded.controllerMethod());
            assertEquals("type=XML_TO_XML", recorded.requestParameters());
            assertEquals("<hello/>", recorded.requestBody());
            assertEquals(200, recorded.responseStatus());
            assertEquals("<hello/>", recorded.responseBody());
            assertTrue(recorded.durationMilliseconds() >= 0.0);
        }

        @Test
        void recordsErrorMessageWhenExceptionThrown() {
            authenticateAs("alice", 42L);
            response.setStatus(500);

            interceptor.afterCompletion(request, response, handlerMethod, new IllegalStateException("Failure"));

            ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
            verify(auditService).record(captor.capture());
            assertEquals(500, captor.getValue().responseStatus());
            assertEquals("IllegalStateException: Failure", captor.getValue().errorMessage());
        }

        @Test
        void skipsWhenUserIsNotAuthenticated() {
            interceptor.afterCompletion(request, response, handlerMethod, null);

            verifyNoInteractions(auditService);
        }

        @Test
        void skipsWhenHandlerIsNotControllerMethod() {
            authenticateAs("alice", 42L);

            interceptor.afterCompletion(request, response, new Object(), null);

            verifyNoInteractions(auditService);
        }
    }

    private void authenticateAs(String username, long userId) {
        User user = new User(username, "hash");
        user.setId(userId);
        AuthUser authUser = new AuthUser(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(authUser, "", authUser.getAuthorities())
        );
    }

    private ContentCachingRequestWrapper wrapRequest(String body) throws IOException {
        request.setContentType(MediaType.APPLICATION_XML_VALUE);
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request, 1024);
        wrapper.getInputStream().readAllBytes();
        return wrapper;
    }

    private ContentCachingResponseWrapper wrapResponse(String body) throws IOException {
        ContentCachingResponseWrapper wrapper = new ContentCachingResponseWrapper(response);
        wrapper.setStatus(200);
        wrapper.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        return wrapper;
    }

    private static class SampleController {
        void handle() {
        }
    }
}
